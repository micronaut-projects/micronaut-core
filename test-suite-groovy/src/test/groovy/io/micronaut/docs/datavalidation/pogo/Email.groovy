package io.micronaut.docs.datavalidation.pogo;

import io.micronaut.core.annotation.Introspected;

import javax.validation.constraints.NotBlank;

@Introspected
class Email {

    @NotBlank // <1>
    String subject;

    @NotBlank // <1>
    String recipient;
}
