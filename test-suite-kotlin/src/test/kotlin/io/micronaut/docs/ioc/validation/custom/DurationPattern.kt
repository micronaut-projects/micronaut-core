package io.micronaut.docs.ioc.validation.custom

// tag::imports[]
import javax.validation.Constraint
// end::imports[]

// tag::class[]
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = []) // <1>
annotation class DurationPattern(
    val message: String = "invalid duration ({validatedValue})" // <2>
)
// end::class[]