package io.github.jastname.playwrighttester.controller;

import io.github.jastname.playwrighttester.config.LoginProperties;
import io.github.jastname.playwrighttester.config.ScreenshotProperties;
import io.github.jastname.playwrighttester.config.ServerProperties;
import io.github.jastname.playwrighttester.service.PlaywrightService;
import io.github.jastname.playwrighttester.dto.ButtonCandidate;
import io.github.jastname.playwrighttester.service.InspectorSessionStore;
import io.github.jastname.playwrighttester.service.ScenarioProgressStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.SequenceInputStream;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
@RestController
@RequestMapping("/api/browser")
@CrossOrigin(origins = "*")
public class BrowserTestController {

    private final PlaywrightService playwrightService;
    private final InspectorSessionStore inspectorSessionStore = new InspectorSessionStore();
    private final ScreenshotProperties screenshotProperties;
    private final ServerProperties serverProperties;
    private final ScenarioProgressStore progressStore;

    // ── 스크린샷 목록 캐시 ──────────────────────────────────────────────────────
    /** 캐싱된 전체 파일 목록 (최신순 정렬). 변경 시 null로 초기화 */
    private volatile List<Map<String, Object>> screenshotCache = null;
    
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    String sUrl;
    
    
    @Autowired
    BrowserTestController(PlaywrightService playwrightService, ScreenshotProperties screenshotProperties,
                          ServerProperties serverProperties, ScenarioProgressStore progressStore) throws URISyntaxException {
        this.playwrightService = playwrightService;
        this.screenshotProperties = screenshotProperties;
        this.serverProperties = serverProperties;
        this.progressStore = progressStore;
        this.sUrl = "http://localhost:" + serverProperties.getPort() + "/";
    		
		System.out.println("Copyright ALL LANDSOFT, Co. Ltd");
         
         try {
            String[] cmd = null;
           try {
              // 실행 커맨드 명령어 취득
              cmd = getUrlCmd(sUrl);
           } catch (Exception e1) {
              e1.printStackTrace();
              return;
           }

           if (cmd == null) {
              System.out.println("실행할 커맨드가 없습니다.");
           } else {
              // 커맨드 실행
              
                 executeCmd(cmd);
                 LOGGER.info("브라우저 실행 =>" + sUrl);
           }

         }catch (Exception e) {
            LOGGER.error("브라우저 로딩 오류");
         }
    }

    
    /**
     * OS를 확인하여 그에 해당하는 커맨드를 설정하여 반환한다.
     * @param url
     * @return
     * @throws Exception
     */
    private static String[] getUrlCmd(String url) throws Exception {
       String[] cmd = null;
       // OS확인
       if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
          // windows일 경우
          cmd = new String[] {"rundll32", "url.dll", "FileProtocolHandler",  url};
       } else {
          // windows이외인 경우
          String[] browsers = {"firefox", "mozilla", "konqueror", "eqiphany", "netscape"};
          String browser = "";

          try {
             for (int i = 0; i < browsers.length && "".equals(browser); i ++) {
                // which로 깔려있는 인터넷 브라우저를 탐색
                if (new ProcessBuilder(new String[] { "which", browsers[i] }).start().waitFor() == 0) {
                   browser = browsers[i];
                }
             }

             // 브라우저가 아무것도 없을면
             if ("".equals(browser)) {
                // 브라우저가 없을 경우 Exception발생 시킴
                throw new Exception("Could not find web browser");
             } else {
                cmd = new String[]{browser, url};
             }
          } catch (IOException e) {
             e.printStackTrace();
          } catch (InterruptedException e) {
             e.printStackTrace();
          }
       }

       return cmd;
    }
    
    
    /**
     * 커맨드 실행
     * @param cmd
     */
    @SuppressWarnings("resource")
    private static void executeCmd(String[] cmd) {

       Process process = null;
       try {

          // 프로세스빌더 실행
          process = new ProcessBuilder(cmd).start();

          // SequenceInputStream은 여러개의 스트림을 하나의 스트림으로 연결해줌.
          SequenceInputStream seqIn = new SequenceInputStream(process.getInputStream(), process.getErrorStream());

          // 스캐너클래스를 사용해 InputStream을 스캔함
          Scanner s = new Scanner(seqIn);
          while (s.hasNextLine() == true) {
             // 표준출력으로 출력
             System.out.println(s.nextLine());
          }

       } catch (IOException e) {
          e.printStackTrace();
       }
    }

    
    
    @GetMapping("/settings/screenshot-directory")
    public Map<String, Object> getScreenshotDirectory() {
        return Map.of(
                "directory", screenshotProperties.getDirectory(),
                "absolutePath", screenshotProperties.getDirectoryPath().toString()
        );
    }

    @PutMapping("/settings/screenshot-directory")
    public Map<String, Object> setScreenshotDirectory(@RequestBody Map<String, Object> body) throws IOException {
        Object directoryValue = body.get("directory");
        if (!(directoryValue instanceof String directory) || directory.isBlank()) {
            return Map.of("ok", false, "error", "directory is required");
        }

        screenshotProperties.saveDirectory(directory.trim());
        Path dir = screenshotProperties.getDirectoryPath();
        Files.createDirectories(dir);
        screenshotCache = null;

        return Map.of(
                "ok", true,
                "directory", screenshotProperties.getDirectory(),
                "absolutePath", dir.toString()
        );
    }

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
                request.getScenarioName(),
                request.getViewport(),
                Boolean.TRUE.equals(request.getFullPageScreenshot())
        );
        screenshotCache = null; // 새 스크린샷 저장 → 캐시 무효화
        return result;
    }

    /**
     * 시나리오를 비동기로 실행하기 시작하고 executionId를 즉시 반환합니다.
     * 클라이언트는 이후 GET /test-scenario-progress/{id} 로 SSE 연결해 진행률을 수신합니다.
     */
    @PostMapping("/test-scenario-async")
    public Map<String, Object> testScenarioAsync(@Valid @RequestBody ScenarioRequest request) {
        ScenarioProgressStore.Execution exec = progressStore.create();

        Thread.ofVirtual().start(() -> {
            try {
                playwrightService.testScenario(
                        request.getUrl(),
                        request.getBrowser(),
                        request.getHeadless(),
                        request.getTimeout(),
                        request.getSteps(),
                        request.getScenarioId(),
                        request.getScenarioName(),
                        request.getViewport(),
                        Boolean.TRUE.equals(request.getFullPageScreenshot()),
                        event -> exec.emit((String) event.get("type"), event)
                );
            } catch (Exception e) {
                exec.emit("scenario-error", Map.of(
                    "type",         "scenario-error",
                    "errorMessage", e.getMessage() != null ? e.getMessage() : "알 수 없는 오류"
                ));
            } finally {
                screenshotCache = null;
                exec.complete();
                // 60초 후 자원 정리
                try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
                progressStore.remove(exec.id);
            }
        });

        return Map.of("executionId", exec.id);
    }

    /**
     * SSE 스트림: 시나리오 실행 진행률을 실시간으로 전송합니다.
     */
    @GetMapping(value = "/test-scenario-progress/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter testScenarioProgress(@PathVariable("id") String id) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5분 타임아웃
        ScenarioProgressStore.Execution exec = progressStore.get(id);
        if (exec == null) {
            emitter.completeWithError(new IllegalArgumentException("execution not found: " + id));
            return emitter;
        }
        exec.addEmitter(emitter);
        return emitter;
    }

    // ── UI 인스펙터 ───────────────────────────────────────────────────────────

    /** 인스펙터 세션 시작: 브라우저 열고 sessionId 반환 */
    @PostMapping("/inspector/start")
    public Map<String, Object> inspectorStart(@RequestBody Map<String, Object> body) {
        String url     = (String) body.getOrDefault("url", "about:blank");
        String browser = (String) body.getOrDefault("browser", "chromium");
        int timeout    = body.containsKey("timeout") ? ((Number) body.get("timeout")).intValue() : 30000;

        // viewport 파싱
        ScenarioRequest.ViewportConfig viewport = null;
        Object vpObj = body.get("viewport");
        if (vpObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> vpMap = (Map<String, Object>) vpObj;
            viewport = new ScenarioRequest.ViewportConfig();
            if (vpMap.get("width")             instanceof Number n) viewport.setWidth(n.intValue());
            if (vpMap.get("height")            instanceof Number n) viewport.setHeight(n.intValue());
            if (vpMap.get("deviceName")        instanceof String s) viewport.setDeviceName(s);
            if (vpMap.get("userAgent")         instanceof String s) viewport.setUserAgent(s);
            if (vpMap.get("deviceScaleFactor") instanceof Number n) viewport.setDeviceScaleFactor(n.doubleValue());
            if (vpMap.get("isMobile")          instanceof Boolean b) viewport.setIsMobile(b);
            if (vpMap.get("hasTouch")          instanceof Boolean b) viewport.setHasTouch(b);
        }

        InspectorSessionStore.Session session = inspectorSessionStore.create();
        playwrightService.startInspector(url, browser, timeout, session, viewport);

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
        Path dir = screenshotProperties.getDirectoryPath();
        if (!Files.exists(dir)) {
            return Map.of(
                    "count", 0,
                    "totalBytes", 0L,
                    "totalPages", 0,
                    "page", page,
                    "files", List.of(),
                    "directory", dir.toString()
            );
        }

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
                "files",      pageItems,
                "directory",  dir.toString()
        );
    }

    @GetMapping("/screenshots/file/{filename}")
    public ResponseEntity<Resource> screenshotFile(@PathVariable("filename") String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        Path file = screenshotProperties.getDirectoryPath().resolve(filename).normalize();
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }

    @DeleteMapping("/screenshots")
    public Map<String, Object> screenshotDeleteAll() throws IOException {
        Path dir = screenshotProperties.getDirectoryPath();
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
        Path dir  = screenshotProperties.getDirectoryPath();
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
        Path dir = screenshotProperties.getDirectoryPath();
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
