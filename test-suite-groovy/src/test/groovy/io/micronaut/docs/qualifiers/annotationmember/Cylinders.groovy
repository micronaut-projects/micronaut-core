package io.micronaut.docs.qualifiers.annotationmember

// tag::imports[]
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]

// tag::class[]
@Qualifier // <1>
@Retention(RUNTIME)
@interface Cylinders {
    int value();

    @NonBinding // <2>
    String description() default "";
}
// end::class[]
