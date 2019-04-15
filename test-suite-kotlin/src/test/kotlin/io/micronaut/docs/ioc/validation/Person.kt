package io.micronaut.docs.ioc.validation

// tag::class[]
import io.micronaut.core.annotation.Introspected
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

@Introspected
data class Person(
    @field:NotBlank var name: String,
    @field:Min(18) var age: Int
)
// end::class[]