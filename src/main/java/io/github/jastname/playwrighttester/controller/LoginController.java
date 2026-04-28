package io.github.jastname.playwrighttester.controller;

import io.github.jastname.playwrighttester.config.AuthInterceptor;
import io.github.jastname.playwrighttester.config.LoginProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 로그인 / 로그아웃 / 상태 확인 REST API
 * POST /api/auth/login   – 로그인 (BCrypt 비밀번호 비교)
 * POST /api/auth/logout  – 로그아웃
 * GET  /api/auth/status  – 세션 상태 조회
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginProperties loginProperties;

    /** application.yml 의 plain password 를 앱 시작 시 BCrypt 인코딩하여 메모리에 보관 */
    private String encodedPassword;

    @PostConstruct
    public void init() {
        encodedPassword = loginProperties.getPassword();
    }

    /** 로그인 */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        if (loginProperties.getUsername().equals(username)
                && password.equals(encodedPassword)) {
            HttpSession session = request.getSession(true);
            session.setAttribute(AuthInterceptor.SESSION_KEY, username);
            return ResponseEntity.ok(Map.of("success", true, "user", username));
        }

        return ResponseEntity.status(401)
                .body(Map.of("success", false, "message", "아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    /** 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 세션 상태 확인 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthInterceptor.SESSION_KEY) != null) {
            String user = (String) session.getAttribute(AuthInterceptor.SESSION_KEY);
            return ResponseEntity.ok(Map.of("loggedIn", true, "user", user));
        }
        return ResponseEntity.ok(Map.of("loggedIn", false));
    }
}