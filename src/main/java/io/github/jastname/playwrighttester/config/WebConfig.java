package io.github.jastname.playwrighttester.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/screenshots/**")
                .addResourceLocations(
                        Paths.get("screenshots").toAbsolutePath().toUri().toString()
                );
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login.html",           // 로그인 페이지
                        "/api/auth/**",          // 로그인·로그아웃·상태 API
                        "/style.css",
                        "/js/**",
                        "/screenshots/**",
                        "/favicon.ico",
                        "/error"
                );
    }
}