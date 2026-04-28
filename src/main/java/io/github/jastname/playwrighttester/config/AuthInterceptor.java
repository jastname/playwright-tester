package io.github.jastname.playwrighttester.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증 인터셉터
 * - 세션에 LOGIN_USER 없으면:
 *   · HTML 요청(Accept: text/html) → /login 으로 리다이렉트
 *   · API / fetch 요청 → 401 JSON 반환
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "LOGIN_USER";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        boolean loggedIn = session != null && session.getAttribute(SESSION_KEY) != null;
        if (loggedIn) return true;

        String accept = request.getHeader("Accept");
        boolean wantsHtml = accept != null && accept.contains("text/html");

        if (wantsHtml) {
            // 원래 가려던 URL 을 redirect 파라미터로 전달
            String requestUri = request.getRequestURI();
            String query = request.getQueryString();
            String original = query != null ? requestUri + "?" + query : requestUri;
            response.sendRedirect("/login.html?redirect=" + java.net.URLEncoder.encode(original, "UTF-8"));
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}");
        }
        return false;
    }
}