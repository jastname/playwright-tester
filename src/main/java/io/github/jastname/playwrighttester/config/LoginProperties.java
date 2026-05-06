package io.github.jastname.playwrighttester.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.login")
public class LoginProperties {
    private String username = "landsoft";
    private String password = "landsoft13!#";
    /** 로그인 유지시간 (초). 기본값 1800초 = 30분 */
    private int sessionTimeout = 10800;
}
