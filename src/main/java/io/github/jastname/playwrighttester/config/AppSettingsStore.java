package io.github.jastname.playwrighttester.config;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Component
public class AppSettingsStore {
    private static final Path SETTINGS_PATH = Paths.get("settings", "playwright-tester.properties");
    private static final Path LEGACY_SCREENSHOT_PATH = Paths.get("settings", "screenshot-directory.txt");
    private static final Path LEGACY_SCENARIO_PATH = Paths.get("settings", "scenario-file.txt");

    public synchronized String get(String key) {
        return load().getProperty(key);
    }

    public synchronized void set(String key, String value) throws IOException {
        Properties props = load();
        props.setProperty(key, value);
        save(props);
    }

    private Properties load() {
        Properties props = new Properties();
        if (Files.exists(SETTINGS_PATH)) {
            try (Reader reader = Files.newBufferedReader(SETTINGS_PATH)) {
                props.load(reader);
            } catch (IOException ignored) {}
        }
        loadLegacyValue(props, "screenshot.directory", LEGACY_SCREENSHOT_PATH);
        loadLegacyValue(props, "scenario.file", LEGACY_SCENARIO_PATH);
        return props;
    }

    private void loadLegacyValue(Properties props, String key, Path legacyPath) {
        if (props.containsKey(key) || !Files.exists(legacyPath)) return;
        try {
            String value = Files.readString(legacyPath).trim();
            if (!value.isBlank()) props.setProperty(key, value);
        } catch (IOException ignored) {}
    }

    private void save(Properties props) throws IOException {
        Files.createDirectories(SETTINGS_PATH.getParent());
        try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH)) {
            props.store(writer, "Playwright tester settings");
        }
    }
}
