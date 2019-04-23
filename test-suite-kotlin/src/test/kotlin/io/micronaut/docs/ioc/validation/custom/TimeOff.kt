package io.micronaut.docs.ioc.validation.custom

// tag::class[]
@Retention(AnnotationRetention.RUNTIME)
annotation class TimeOff(
    @DurationPattern val duration: String
)
// end::class[]
