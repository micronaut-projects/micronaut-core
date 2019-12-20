package io.micronaut.docs.client.filter

//tag::class[]
import io.micronaut.http.annotation.FilterMatcher

@FilterMatcher // <1>
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
annotation class BasicAuth
//end::class[]
