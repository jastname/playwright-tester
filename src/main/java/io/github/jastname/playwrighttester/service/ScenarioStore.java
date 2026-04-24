package io.github.jastname.playwrighttester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 시나리오를 JSON 파일로 영속화합니다.
 * 파일 위치: {workingDir}/scenarios/scenarios.json
 *
 * 성능 개선: 파일 내용을 메모리(ArrayNode)에 캐싱합니다.
 * - 읽기: 캐시에서 즉시 반환 (파일 I/O 없음)
 * - 쓰기: 파일 저장 후 캐시 갱신
 * - ReadWriteLock으로 동시 접근 안전 보장
 */
@Service
public class ScenarioStore {

    private static final Path SAVE_PATH = Paths.get("scenarios", "scenarios.json");
    private final ObjectMapper mapper = new ObjectMapper();

    /** 메모리 캐시 */
    private volatile ArrayNode cache = null;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            if (!Files.exists(SAVE_PATH)) Files.writeString(SAVE_PATH, "[]");
            System.out.println("[ScenarioStore] 시나리오 파일: " + SAVE_PATH.toAbsolutePath());
            // 초기 캐시 로드
            warmCache();
        } catch (IOException e) {
            System.err.println("[ScenarioStore] init error: " + e.getMessage());
            cache = mapper.createArrayNode();
        }
    }

    private void warmCache() {
        try {
            String raw = Files.readString(SAVE_PATH);
            JsonNode node = mapper.readTree(raw);
            cache = node.isArray() ? (ArrayNode) node : mapper.createArrayNode();
        } catch (Exception e) {
            System.err.println("[ScenarioStore] warmCache error: " + e.getMessage());
            cache = mapper.createArrayNode();
        }
    }

    /** 캐시에서 JSON 문자열 반환 (파일 I/O 없음) */
    public String loadRaw() {
        lock.readLock().lock();
        try {
            if (cache == null) warmCache();
            return cache.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 파일 저장 후 캐시 갱신 */
    public void saveRaw(String json) {
        lock.writeLock().lock();
        try {
            Files.writeString(SAVE_PATH, json);
            JsonNode node = mapper.readTree(json);
            cache = node.isArray() ? (ArrayNode) node : mapper.createArrayNode();
        } catch (Exception e) {
            System.err.println("[ScenarioStore] save error: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** ID로 단일 시나리오 반환 – 캐시 탐색 */
    public String loadById(long id) {
        lock.readLock().lock();
        try {
            if (cache == null) warmCache();
            for (JsonNode node : cache) {
                if (node.path("id").asLong() == id) {
                    return mapper.writeValueAsString(node);
                }
            }
        } catch (Exception e) {
            System.err.println("[ScenarioStore] loadById error: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    /** ID로 시나리오를 새 JSON으로 교체 후 저장 */
    public boolean updateById(long id, String updatedJson) {
        lock.writeLock().lock();
        try {
            if (cache == null) warmCache();
            ArrayNode result = mapper.createArrayNode();
            boolean found = false;
            for (JsonNode node : cache) {
                if (node.path("id").asLong() == id) {
                    result.add(mapper.readTree(updatedJson));
                    found = true;
                } else {
                    result.add(node);
                }
            }
            if (found) {
                cache = result;
                Files.writeString(SAVE_PATH, mapper.writeValueAsString(result));
            }
            return found;
        } catch (Exception e) {
            System.err.println("[ScenarioStore] updateById error: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** ID로 시나리오 삭제 후 저장 */
    public boolean deleteById(long id) {
        lock.writeLock().lock();
        try {
            if (cache == null) warmCache();
            ArrayNode result = mapper.createArrayNode();
            boolean found = false;
            for (JsonNode node : cache) {
                if (node.path("id").asLong() == id) { found = true; }
                else result.add(node);
            }
            if (found) {
                cache = result;
                Files.writeString(SAVE_PATH, mapper.writeValueAsString(result));
            }
            return found;
        } catch (Exception e) {
            System.err.println("[ScenarioStore] deleteById error: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path getSavePath() { return SAVE_PATH.toAbsolutePath(); }
}