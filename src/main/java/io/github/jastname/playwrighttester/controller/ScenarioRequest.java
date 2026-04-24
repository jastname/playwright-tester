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

    /** 시나리오 ID (스크린샷 메타 연결용, optional) */
    private Long scenarioId;

    /** 시나리오 이름 (스크린샷 메타 연결용, optional) */
    private String scenarioName;

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

        // 실제 단계 순서 (스크린샷 메타 저장용, optional)
        private Integer order;
    }
}