package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.core.annotation.Introspected
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank

@Introspected
class Pojo {

    @Email(message = "Email should be valid")
    var email: String? = null

    @NotBlank
    var name: String? = null
}

