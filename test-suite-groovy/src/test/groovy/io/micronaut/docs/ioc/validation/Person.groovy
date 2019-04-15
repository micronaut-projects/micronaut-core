package io.micronaut.docs.ioc.validation

// tag::class[]
import io.micronaut.core.annotation.Introspected

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

@Introspected
class Person {
    @NotBlank
    String name
    @Min(18l)
    int age
}
// end::class[]
