package io.micronaut.kotlin.processing.inject.configproperties.itfce

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Requires
import jakarta.validation.constraints.NotBlank

@EachProperty(value = "my.config", primary = "default")
@Requires(property = "my.config")
interface MyEachConfig {

    @NotBlank
    fun getName(): String
}
