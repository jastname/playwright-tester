package io.github.jastname.playwrighttester.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 시나리오를 JSON 파일로 영속화합니다.
 * 파일 위치: {workingDir}/scenarios/scenarios.json
 * Jackson 없이 Spring Web 내장 JSON 변환 대신 직렬화는 프론트에서 받은 raw JSON 문자열 그대로 저장합니다.
 */
@Service
public class ScenarioStore {

    private static final Path SAVE_PATH = Paths.get("scenarios", "scenarios.json");

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            if (!Files.exists(SAVE_PATH)) Files.writeString(SAVE_PATH, "[]");
            System.out.println("[ScenarioStore] 시나리오 파일: " + SAVE_PATH.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ScenarioStore] init error: " + e.getMessage());
        }
    }

    /** 저장된 JSON 문자열 그대로 반환 (Spring MVC가 알아서 직렬화) */
    public String loadRaw() {
        try {
            return Files.readString(SAVE_PATH);
        } catch (Exception e) {
            System.err.println("[ScenarioStore] load error: " + e.getMessage());
            return "[]";
        }
    }

    /** 프론트에서 받은 raw JSON 문자열을 파일에 저장 */
    public void saveRaw(String json) {
        try {
            Files.writeString(SAVE_PATH, json);
        } catch (Exception e) {
            System.err.println("[ScenarioStore] save error: " + e.getMessage());
        }
    }

    public Path getSavePath() { return SAVE_PATH.toAbsolutePath(); }
}