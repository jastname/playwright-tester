package io.github.jastname.playwrighttester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.jastname.playwrighttester.config.AppSettingsStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ScenarioStore {

    private static final Path DEFAULT_SAVE_PATH = Paths.get("scenarios", "scenarios.json");
    private static final String SETTINGS_KEY = "scenario.file";

    private final AppSettingsStore appSettingsStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile Path savePath = DEFAULT_SAVE_PATH;
    private volatile ArrayNode cache = null;

    public ScenarioStore(AppSettingsStore appSettingsStore) {
        this.appSettingsStore = appSettingsStore;
    }

    @PostConstruct
    public void init() {
        loadSavedPath();
        try {
            ensureScenarioFile();
        } catch (IOException e) {
            System.err.println("[ScenarioStore] init error: " + e.getMessage());
            cache = mapper.createArrayNode();
        }
        warmCache();
    }

    private void loadSavedPath() {
        setSavePath(appSettingsStore.get(SETTINGS_KEY));
    }

    private void ensureScenarioFile() throws IOException {
        Path parent = savePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(savePath)) Files.writeString(savePath, "[]");
        System.out.println("[ScenarioStore] scenario file: " + savePath.toAbsolutePath());
    }

    private void warmCache() {
        try {
            String raw = Files.readString(savePath);
            JsonNode node = mapper.readTree(raw);
            cache = node.isArray() ? (ArrayNode) node : mapper.createArrayNode();
        } catch (Exception e) {
            System.err.println("[ScenarioStore] warmCache error: " + e.getMessage());
            cache = mapper.createArrayNode();
        }
    }

    public String loadRaw() {
        lock.readLock().lock();
        try {
            if (cache == null) warmCache();
            return cache.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveRaw(String json) {
        lock.writeLock().lock();
        try {
            ensureScenarioFile();
            Files.writeString(savePath, json);
            JsonNode node = mapper.readTree(json);
            cache = node.isArray() ? (ArrayNode) node : mapper.createArrayNode();
        } catch (Exception e) {
            System.err.println("[ScenarioStore] save error: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

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
                Files.writeString(savePath, mapper.writeValueAsString(result));
            }
            return found;
        } catch (Exception e) {
            System.err.println("[ScenarioStore] updateById error: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteById(long id) {
        lock.writeLock().lock();
        try {
            if (cache == null) warmCache();
            ArrayNode result = mapper.createArrayNode();
            boolean found = false;
            for (JsonNode node : cache) {
                if (node.path("id").asLong() == id) {
                    found = true;
                } else {
                    result.add(node);
                }
            }
            if (found) {
                cache = result;
                Files.writeString(savePath, mapper.writeValueAsString(result));
            }
            return found;
        } catch (Exception e) {
            System.err.println("[ScenarioStore] deleteById error: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path getSavePath() {
        return savePath.toAbsolutePath().normalize();
    }

    public String getConfiguredPath() {
        return savePath.toString();
    }

    public void saveScenarioPath(String path) throws IOException {
        lock.writeLock().lock();
        try {
            setSavePath(path);
            ensureScenarioFile();
            appSettingsStore.set(SETTINGS_KEY, savePath.toString());
            warmCache();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void setSavePath(String path) {
        if (path == null || path.isBlank()) {
            savePath = DEFAULT_SAVE_PATH;
            return;
        }
        savePath = Paths.get(path.trim()).toAbsolutePath().normalize();
    }
}
