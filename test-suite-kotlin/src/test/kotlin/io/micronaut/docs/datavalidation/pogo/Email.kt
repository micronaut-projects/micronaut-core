package io.micronaut.docs.datavalidation.pogo

import io.micronaut.core.annotation.Introspected

import javax.validation.constraints.NotBlank

@Introspected
open class Email {

    @NotBlank // <1>
    var subject: String? = null

    @NotBlank // <1>
    var recipient: String? = null
}
