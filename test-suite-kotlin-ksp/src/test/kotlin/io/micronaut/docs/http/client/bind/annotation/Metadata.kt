package io.micronaut.docs.http.client.bind.annotation

//tag::clazz[]
import io.micronaut.core.bind.annotation.Bindable
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@MustBeDocumented
@Retention(RUNTIME)
@Target(VALUE_PARAMETER)
@Bindable
annotation class Metadata
//end::clazz[]
