package io.github.jastname.playwrighttester.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ScreenshotProperties {
    private static final String SETTINGS_KEY = "screenshot.directory";

    private final AppSettingsStore appSettingsStore;
    private String directory = "screenshots";

    public ScreenshotProperties(AppSettingsStore appSettingsStore) {
        this.appSettingsStore = appSettingsStore;
    }

    @PostConstruct
    public void loadSavedDirectory() {
        setDirectory(appSettingsStore.get(SETTINGS_KEY));
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            this.directory = "screenshots";
            return;
        }
        this.directory = directory.trim();
    }

    public Path getDirectoryPath() {
        return Paths.get(directory).toAbsolutePath().normalize();
    }

    public void saveDirectory(String directory) throws IOException {
        setDirectory(directory);
        appSettingsStore.set(SETTINGS_KEY, this.directory);
    }
}
