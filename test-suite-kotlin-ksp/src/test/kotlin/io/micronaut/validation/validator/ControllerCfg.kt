package io.micronaut.validation.validator

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.NotBlank

@ConfigurationProperties("app.controller")
data class ControllerCfg(
    val enabled: Boolean = true,
    @NotBlank val apiBase:String = "https://api.example.com/",
    @NotBlank val something: String = "some-value"
)
