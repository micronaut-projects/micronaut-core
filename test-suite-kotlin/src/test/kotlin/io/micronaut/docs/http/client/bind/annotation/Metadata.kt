package io.micronaut.docs.http.client.bind.annotation

//tag::clazz[]
import io.micronaut.core.bind.annotation.Bindable

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
@Bindable
annotation class Metadata
//end::clazz[]
