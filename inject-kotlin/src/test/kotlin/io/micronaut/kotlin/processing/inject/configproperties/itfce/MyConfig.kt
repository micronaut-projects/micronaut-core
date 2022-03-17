package io.micronaut.kotlin.processing.inject.configproperties.itfce

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import javax.validation.constraints.NotBlank

@ConfigurationProperties("my.config")
@Requires(property = "my.config")
interface MyConfig {

    @NotBlank
    fun getName(): String
}
