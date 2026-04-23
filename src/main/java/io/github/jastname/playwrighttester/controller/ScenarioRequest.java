package io.github.jastname.playwrighttester.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScenarioRequest {

    @NotBlank
    @Pattern(regexp = "https?://.*")
    private String url;

    @NotBlank
    @Pattern(regexp = "chromium|firefox|webkit")
    private String browser;

    @NotNull
    private Boolean headless;

    @NotNull
    @Min(1000)
    @Max(300000)
    private Integer timeout;

    @NotEmpty
    @Valid
    private List<ScenarioStep> steps;

    @Getter
    @Setter
    public static class ScenarioStep {
        @NotBlank
        private String selector;

        @NotBlank
        @Pattern(regexp = "click|fill|select")
        private String interactionType;

        private String fillText;

        // 각 액션 후 대기 (ms), 기본 0
        private Integer waitMs;
    }
}
