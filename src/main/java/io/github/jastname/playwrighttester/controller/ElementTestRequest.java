package io.github.jastname.playwrighttester.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ElementTestRequest {

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

    @NotBlank
    private String selector;

    @NotBlank
    @Pattern(regexp = "click|fill|select")
    private String interactionType;

    // fill 타입일 때 입력할 텍스트 (선택, 없으면 기본값 사용)
    private String fillText;
}
