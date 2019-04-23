package io.micronaut.docs.ioc.validation.custom

// tag::imports[]
import java.lang.annotation.*
// end::imports[]

// tag::class[]
@Retention(RetentionPolicy.RUNTIME)
@interface TimeOff {
    @DurationPattern
    String duration()
}
// end::class[]

