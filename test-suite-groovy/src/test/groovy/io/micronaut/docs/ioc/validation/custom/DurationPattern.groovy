package io.micronaut.docs.ioc.validation.custom

// tag::imports[]
import javax.validation.Constraint
import java.lang.annotation.*

import static java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]

// tag::class[]
@Retention(RUNTIME)
@Constraint(validatedBy = []) // <1>
@interface DurationPattern {
    String message() default "invalid duration ({validatedValue})" // <2>
}
// end::class[]

