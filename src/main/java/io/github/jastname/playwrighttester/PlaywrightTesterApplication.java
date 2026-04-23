package io.github.jastname.playwrighttester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.jastname.playwrighttester.config.PlaywrightProperties;

@SpringBootApplication
@EnableConfigurationProperties(PlaywrightProperties.class)
@EnableScheduling
public class PlaywrightTesterApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlaywrightTesterApplication.class, args);
	}

}
