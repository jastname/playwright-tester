package io.github.jastname.playwrighttester.controller;

import io.github.jastname.playwrighttester.service.ScenarioStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 시나리오 영속화 API
 * GET  /api/scenarios       → 저장된 시나리오 목록 반환
 * POST /api/scenarios       → 전체 목록을 파일에 저장
 */
@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScenarioController {

    private final ScenarioStore scenarioStore;

    /** 저장된 시나리오 목록 반환 (JSON 파일 내용을 그대로 응답) */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> list() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(scenarioStore.loadRaw());
    }

    /** ID로 시나리오 조회 */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getById(@PathVariable("id") long id) {
        String json = scenarioStore.loadById(id);
        if (json == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /** 전체 목록을 파일에 저장 (요청 body를 그대로 파일에 기록) */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> save(@RequestBody String body) {
        scenarioStore.saveRaw(body);
        return Map.of(
            "ok", true,
            "path", scenarioStore.getSavePath().toString()
        );
    }

    /** ID로 시나리오 수정 */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateById(@PathVariable("id") long id, @RequestBody String body) {
        boolean updated = scenarioStore.updateById(id, body);
        if (!updated) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** ID로 시나리오 삭제 */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteById(@PathVariable("id") long id) {
        boolean deleted = scenarioStore.deleteById(id);
        return Map.of("ok", deleted);
    }
}