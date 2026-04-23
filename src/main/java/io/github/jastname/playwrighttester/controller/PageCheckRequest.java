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
public class PageCheckRequest {

    @NotBlank(message = "URL은 필수입니다.")
    @Pattern(
            regexp = "https?://.*",
            message = "http 또는 https URL만 허용됩니다."
    )
    private String url;

    @NotBlank(message = "브라우저는 필수입니다.")
    @Pattern(
            regexp = "chromium|firefox|webkit",
            message = "브라우저는 chromium, firefox, webkit 중 하나여야 합니다."
    )
    private String browser;

    @NotNull(message = "headless 값은 필수입니다.")
    private Boolean headless;

    @NotNull(message = "timeout 값은 필수입니다.")
    @Min(value = 1000, message = "timeout은 최소 1000ms 이상이어야 합니다.")
    @Max(value = 300000, message = "timeout은 최대 300000ms 이하이어야 합니다.")
    private Integer timeout;

    @NotNull(message = "fullPageScreenshot 값은 필수입니다.")
    private Boolean fullPageScreenshot;
}