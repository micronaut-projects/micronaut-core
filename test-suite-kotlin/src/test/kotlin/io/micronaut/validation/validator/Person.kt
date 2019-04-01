package io.micronaut.validation.validator

import io.micronaut.core.annotation.Introspected
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

@Introspected
data class Person(
    @NotBlank var name: String,
    @Min(18) var age: Int
)