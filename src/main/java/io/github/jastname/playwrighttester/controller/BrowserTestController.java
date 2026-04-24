package io.github.jastname.playwrighttester.controller;

import io.github.jastname.playwrighttester.service.PlaywrightService;
import io.github.jastname.playwrighttester.dto.ButtonCandidate;
import io.github.jastname.playwrighttester.service.InspectorSessionStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
@RestController
@RequestMapping("/api/browser")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BrowserTestController {

    private final PlaywrightService playwrightService;
    private final InspectorSessionStore inspectorSessionStore;

    // ── 스크린샷 목록 캐시 ──────────────────────────────────────────────────────
    /** 캐싱된 전체 파일 목록 (최신순 정렬). 변경 시 null로 초기화 */
    private volatile List<Map<String, Object>> screenshotCache = null;

    @PostMapping("/check")
    public Map<String, Object> check(@Valid @RequestBody PageCheckRequest request) {
        Map<String, Object> result = playwrightService.checkPage(
                request.getUrl(),
                request.getBrowser(),
                request.getHeadless(),
                request.getTimeout(),
                request.getFullPageScreenshot()
        );
        screenshotCache = null; // 새 스크린샷 저장 → 캐시 무효화
        return result;
    }

    @PostMapping("/scan-elements")
    public List<Map<String, Object>> scanElements(@Valid @RequestBody ButtonScanRequest request) {
        return playwrightService.scanElements(
                request.getUrl(),
                request.getBrowser(),
                request.getHeadless(),
                request.getTimeout()
        );
    }

    @PostMapping("/test-element")
    public Map<String, Object> testElement(@Valid @RequestBody ElementTestRequest request) {
        Map<String, Object> result = playwrightService.testElement(
                request.getUrl(),
                request.getBrowser(),
                request.getHeadless(),
                request.getTimeout(),
                request.getSelector(),
                request.getInteractionType(),
                request.getFillText()
        );
        screenshotCache = null; // 새 스크린샷 저장 → 캐시 무효화
        return result;
    }

    @PostMapping("/test-scenario")
    public Map<String, Object> testScenario(@Valid @RequestBody ScenarioRequest request) {
        Map<String, Object> result = playwrightService.testScenario(
                request.getUrl(),
                request.getBrowser(),
                request.getHeadless(),
                request.getTimeout(),
                request.getSteps(),
                request.getScenarioId(),
                request.getScenarioName()
        );
        screenshotCache = null; // 새 스크린샷 저장 → 캐시 무효화
        return result;
    }

    // ── UI 인스펙터 ───────────────────────────────────────────────────────────

    /** 인스펙터 세션 시작: 브라우저 열고 sessionId 반환 */
    @PostMapping("/inspector/start")
    public Map<String, Object> inspectorStart(@RequestBody Map<String, Object> body) {
        String url     = (String) body.getOrDefault("url", "about:blank");
        String browser = (String) body.getOrDefault("browser", "chromium");
        int timeout    = body.containsKey("timeout") ? ((Number) body.get("timeout")).intValue() : 30000;

        InspectorSessionStore.Session session = inspectorSessionStore.create();
        playwrightService.startInspector(url, browser, timeout, session);

        // 브라우저가 실제로 기동될 때까지 최대 15초 대기 (running=true 될 때까지)
        for (int i = 0; i < 75; i++) {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            if (session.running) break;
            // 에러가 발생한 경우 즉시 반환
            if (session.errorMessage != null && !session.errorMessage.startsWith("네비게이션")) break;
        }

        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("sessionId", session.id);
        result.put("running",   session.running);
        if (session.errorMessage != null) result.put("errorMessage", session.errorMessage);
        return result;
    }

    /** 지금까지 캡처된 요소 목록 폴링 */
    @GetMapping("/inspector/elements/{sessionId}")
    public Map<String, Object> inspectorElements(@PathVariable("sessionId") String sessionId) {
        InspectorSessionStore.Session session = inspectorSessionStore.get(sessionId);
        if (session == null) return Map.of("error", "세션을 찾을 수 없습니다.", "running", false, "elements", List.of());
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("running",  session.running);
        result.put("elements", session.elements);
        if (session.errorMessage != null) result.put("errorMessage", session.errorMessage);
        return result;
    }

    /** 인스펙터 세션 종료 */
    @PostMapping("/inspector/stop/{sessionId}")
    public Map<String, Object> inspectorStop(@PathVariable("sessionId") String sessionId) {
        InspectorSessionStore.Session session = inspectorSessionStore.get(sessionId);
        if (session == null) return Map.of("error", "세션을 찾을 수 없습니다.");
        playwrightService.stopInspector(session);
        inspectorSessionStore.remove(sessionId);
        return Map.of("stopped", true);
    }

    /** fallback: exposeBinding 없이 fetch로 직접 캡처 전송 */
    @PostMapping("/inspector/capture")
    public Map<String, Object> inspectorCapture(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        if (sessionId == null) return Map.of("error", "sessionId 없음");
        InspectorSessionStore.Session session = inspectorSessionStore.get(sessionId);
        if (session == null) return Map.of("error", "세션을 찾을 수 없습니다.");

        @SuppressWarnings("unchecked")
        Map<String, Object> element = (Map<String, Object>) body.get("element");
        if (element != null) {
            var info = new java.util.LinkedHashMap<>(element);
            info.put("capturedAt", System.currentTimeMillis());
            session.elements.add(info);
            System.out.println("[Inspector][fallback] captured: " + info.getOrDefault("selector", "?"));
        }
        return Map.of("ok", true);
    }

    // ── 스크린샷 관리 ──────────────────────────────────────────────────────────

    /** 스크린샷 목록 및 통계 조회 (페이지네이션 지원, 결과 캐싱) */
    @GetMapping("/screenshots")
    public Map<String, Object> screenshotList(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) throws IOException {
        Path dir = Paths.get("screenshots");
        if (!Files.exists(dir)) return Map.of("count", 0, "totalBytes", 0L, "totalPages", 0, "page", page, "files", List.of());

        // 캐시 미스 시에만 파일 시스템 스캔
        List<Map<String, Object>> all = screenshotCache;
        if (all == null) {
            synchronized (this) {
                all = screenshotCache;
                if (all == null) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<Map<String, Object>> files = new ArrayList<>();
                    try (var stream = Files.list(dir)) {
                        for (Path p : stream.filter(p -> p.toString().endsWith(".png")).toList()) {
                            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                            long fileSize = attr.size();
                            java.util.LinkedHashMap<String, Object> entry = new java.util.LinkedHashMap<>();
                            entry.put("name", p.getFileName().toString());
                            entry.put("size", fileSize);
                            entry.put("createdAt", attr.creationTime().toInstant().toString());
                            Path sidecar = dir.resolve(p.getFileName().toString().replace(".png", ".json"));
                            if (Files.exists(sidecar)) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> meta = om.readValue(sidecar.toFile(), Map.class);
                                    entry.put("scenarioId",   meta.get("scenarioId"));
                                    entry.put("scenarioName", meta.get("scenarioName"));
                                    entry.put("stepOrder",    meta.get("stepOrder"));
                                    entry.put("stepStatus",   meta.get("status"));
                                } catch (Exception ignored) {}
                            }
                            files.add(entry);
                        }
                    }
                    files.sort((a, b) -> ((String) b.get("createdAt")).compareTo((String) a.get("createdAt")));
                    screenshotCache = files;
                    all = files;
                }
            }
        }

        long totalBytes = all.stream().mapToLong(e -> ((Number) e.get("size")).longValue()).sum();
        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex   = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageItems = all.subList(fromIndex, toIndex);

        return Map.of(
                "count",      total,
                "totalBytes", totalBytes,
                "totalPages", totalPages,
                "page",       page,
                "size",       size,
                "files",      pageItems
        );
    }

    /** 스크린샷 전체 삭제 */
    @DeleteMapping("/screenshots")
    public Map<String, Object> screenshotDeleteAll() throws IOException {
        Path dir = Paths.get("screenshots");
        if (!Files.exists(dir)) return Map.of("deleted", 0);
        int count = 0;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(p -> p.toString().endsWith(".png")).toList()) {
                Files.delete(p);
                Path sidecar = dir.resolve(p.getFileName().toString().replace(".png", ".json"));
                if (Files.exists(sidecar)) Files.delete(sidecar);
                count++;
            }
        }
        screenshotCache = null; // 캐시 무효화
        return Map.of("deleted", count);
    }

    /** 스크린샷 개별 삭제 */
    @DeleteMapping("/screenshots/{filename}")
    public Map<String, Object> screenshotDeleteOne(@PathVariable("filename") String filename) throws IOException {
        // 경로 탈출 방지
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return Map.of("ok", false, "error", "잘못된 파일명");
        }
        Path dir  = Paths.get("screenshots");
        Path file = dir.resolve(filename);
        if (!Files.exists(file)) return Map.of("ok", false, "error", "파일 없음");
        Files.delete(file);
        Path sidecar = dir.resolve(filename.replace(".png", ".json"));
        if (Files.exists(sidecar)) Files.delete(sidecar);
        screenshotCache = null; // 캐시 무효화
        return Map.of("ok", true);
    }

    /** N일 이상 된 스크린샷 삭제 */
    @DeleteMapping("/screenshots/old")
    public Map<String, Object> screenshotDeleteOld(@RequestParam(name = "days", defaultValue = "7") int days) throws IOException {
        Path dir = Paths.get("screenshots");
        if (!Files.exists(dir)) return Map.of("deleted", 0);
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int count = 0;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(p -> p.toString().endsWith(".png")).toList()) {
                BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                if (attr.creationTime().toInstant().isBefore(cutoff)) {
                    Files.delete(p);
                    Path sidecar = dir.resolve(p.getFileName().toString().replace(".png", ".json"));
                    if (Files.exists(sidecar)) Files.delete(sidecar);
                    count++;
                }
            }
        }
        if (count > 0) screenshotCache = null; // 캐시 무효화
        return Map.of("deleted", count, "olderThanDays", days);
    }
}