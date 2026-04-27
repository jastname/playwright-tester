package io.github.jastname.playwrighttester.service;

import io.github.jastname.playwrighttester.config.ScreenshotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 오래된 스크린샷 자동 정리 스케줄러
 * - 기본: 매일 자정에 실행, 7일 이상 된 파일 삭제
 * - application.yml 에서 설정 변경 가능
 *   screenshot.cleanup.enabled: true
 *   screenshot.cleanup.retain-days: 7
 */
@Slf4j
@Component
@EnableScheduling
public class ScreenshotCleanupScheduler {

    private final ScreenshotProperties screenshotProperties;

    public ScreenshotCleanupScheduler(ScreenshotProperties screenshotProperties) {
        this.screenshotProperties = screenshotProperties;
    }

    @Value("${screenshot.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${screenshot.cleanup.retain-days:7}")
    private int retainDays;

    // 매일 자정 실행
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldScreenshots() {
        if (!enabled) {
            log.info("[ScreenshotCleanup] 비활성화 상태 — 건너뜀");
            return;
        }

        Path dir = screenshotProperties.getDirectoryPath();
        if (!Files.exists(dir)) return;

        Instant cutoff = Instant.now().minus(retainDays, ChronoUnit.DAYS);
        int count = 0;

        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(f -> f.toString().endsWith(".png")).toList()) {
                BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                if (attr.creationTime().toInstant().isBefore(cutoff)) {
                    Files.delete(p);
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("[ScreenshotCleanup] 오류 발생: {}", e.getMessage());
        }

        if (count > 0) {
            log.info("[ScreenshotCleanup] {}일 이상 된 스크린샷 {}개 삭제 완료", retainDays, count);
        } else {
            log.info("[ScreenshotCleanup] 삭제할 오래된 스크린샷 없음 (기준: {}일)", retainDays);
        }
    }
}
