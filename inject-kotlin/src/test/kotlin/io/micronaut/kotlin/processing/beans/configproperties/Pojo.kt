package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.core.annotation.Introspected

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Introspected
class Pojo {

    @Email(message = "Email should be valid")
    var email: String? = null

    @NotBlank
    var name: String? = null

}

