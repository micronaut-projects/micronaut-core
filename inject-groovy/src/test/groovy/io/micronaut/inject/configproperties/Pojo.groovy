package io.micronaut.inject.configproperties

import io.micronaut.core.annotation.Introspected

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Introspected
class Pojo {

    @Email(message = "Email should be valid")
    String email

    @NotBlank
    String name
}

