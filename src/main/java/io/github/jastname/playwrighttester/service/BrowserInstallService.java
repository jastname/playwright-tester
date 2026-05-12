package io.github.jastname.playwrighttester.service;

import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component
@Order(1)
public class BrowserInstallService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BrowserInstallService.class);

    private String cachedSubprocessClasspath = null;

    @Override
    public void run(ApplicationArguments args) {
        log.info("▶ Playwright 브라우저 설치 여부 확인 중...");

        if (isBrowserInstalled()) {
            log.info("✔ Playwright 브라우저가 이미 설치되어 있습니다.");
            return;
        }

        log.warn("⚠ Playwright 브라우저가 설치되지 않았습니다. 자동 설치를 시작합니다...");
        installBrowsers();
    }

    private boolean isBrowserInstalled() {
        try (Playwright playwright = Playwright.create()) {
            var browser = playwright.chromium().launch();
            browser.close();
            return true;
        } catch (Exception e) {
            log.debug("브라우저 실행 테스트 실패: {}", e.getMessage());
            return false;
        }
    }

    // CLI.main()은 내부에서 System.exit()를 호출하므로 서브프로세스로 격리
    private void installBrowsers() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    getJavaExecutable(), "-cp", resolveSubprocessClasspath(),
                    "com.microsoft.playwright.CLI", "install"
            );
            pb.inheritIO();
            pb.redirectErrorStream(true);

            log.info("▶ playwright install 실행 중...");
            int exitCode = pb.start().waitFor();

            if (exitCode == 0) {
                log.info("✔ Playwright 브라우저 설치가 완료되었습니다.");
            } else {
                log.error("✘ Playwright 브라우저 설치 실패 (exit code: {}).", exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("✘ 브라우저 설치 중 인터럽트가 발생했습니다.", e);
        } catch (Exception e) {
            log.error("✘ 브라우저 설치 중 오류가 발생했습니다: {}", e.getMessage(), e);
        }
    }

    /**
     * IDE/bootRun 환경: java.class.path 를 그대로 사용
     * fat JAR 환경: BOOT-INF/lib/ 에서 playwright*.jar, driver-bundle*.jar 만 추출하여 사용
     */
    private synchronized String resolveSubprocessClasspath() throws Exception {
        if (cachedSubprocessClasspath != null) return cachedSubprocessClasspath;

        String cp = System.getProperty("java.class.path");

        // 클래스패스에 여러 항목 → IDE/bootRun 환경
        if (cp.contains(File.pathSeparator)) {
            return cachedSubprocessClasspath = cp;
        }

        File jarFile = new File(cp);
        if (!jarFile.isFile() || !jarFile.getName().endsWith(".jar")) {
            return cachedSubprocessClasspath = cp;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            boolean isFatJar = jar.stream().anyMatch(e -> e.getName().startsWith("BOOT-INF/lib/"));
            if (!isFatJar) {
                return cachedSubprocessClasspath = cp;
            }

            log.info("▶ fat JAR 감지: playwright JAR 추출 중...");
            Path tempDir = Files.createTempDirectory("pw-cli-cp-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteDirectoryQuietly(tempDir)));

            List<String> extracted = new ArrayList<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("BOOT-INF/lib/") || !name.endsWith(".jar")) continue;

                String fileName = Paths.get(name).getFileName().toString();
                if (fileName.startsWith("playwright") || fileName.startsWith("driver-bundle")) {
                    Path dest = tempDir.resolve(fileName);
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    extracted.add(dest.toString());
                }
            }

            if (extracted.isEmpty()) {
                log.warn("fat JAR에서 playwright JAR를 찾지 못했습니다. 원본 클래스패스를 사용합니다.");
                return cachedSubprocessClasspath = cp;
            }

            log.info("✔ playwright JAR {} 개 추출 완료 → {}", extracted.size(), tempDir);
            return cachedSubprocessClasspath = String.join(File.pathSeparator, extracted);
        }
    }

    private static String getJavaExecutable() {
        return ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + "/bin/java");
    }

    private static void deleteDirectoryQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (Exception ignored) {}
    }
}