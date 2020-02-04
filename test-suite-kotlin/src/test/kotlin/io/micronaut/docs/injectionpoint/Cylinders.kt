package io.micronaut.docs.injectionpoint

// tag::class[]
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Cylinders(val value: Int = 8)
// end::class[]
