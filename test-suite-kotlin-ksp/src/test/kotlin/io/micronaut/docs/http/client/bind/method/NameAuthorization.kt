package io.micronaut.docs.http.client.bind.method;

import io.micronaut.context.annotation.AliasFor
import io.micronaut.core.bind.annotation.Bindable
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

//tag::clazz[]
@MustBeDocumented
@Retention(RUNTIME)
@Target(FUNCTION) // <1>
@Bindable
annotation class NameAuthorization(val name: String = "")

//end::clazz[]
