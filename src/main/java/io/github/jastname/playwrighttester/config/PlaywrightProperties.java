package io.github.jastname.playwrighttester.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "playwright")
public class PlaywrightProperties {
    private boolean headless = true;
    private int timeout = 30000;
    private String browser = "chromium";
}