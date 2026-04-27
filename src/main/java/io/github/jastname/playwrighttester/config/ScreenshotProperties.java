package io.github.jastname.playwrighttester.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ScreenshotProperties {
    private static final Path RUNTIME_CONFIG_PATH = Paths.get("settings", "screenshot-directory.txt");

    private String directory = "screenshots";

    @PostConstruct
    public void loadSavedDirectory() {
        try {
            if (Files.exists(RUNTIME_CONFIG_PATH)) {
                setDirectory(Files.readString(RUNTIME_CONFIG_PATH));
            }
        } catch (IOException ignored) {}
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
        Files.createDirectories(RUNTIME_CONFIG_PATH.getParent());
        Files.writeString(RUNTIME_CONFIG_PATH, this.directory);
    }
}
