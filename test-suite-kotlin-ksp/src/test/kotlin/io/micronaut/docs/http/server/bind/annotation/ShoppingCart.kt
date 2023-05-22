package io.micronaut.docs.http.server.bind.annotation

// tag::class[]
import io.micronaut.core.bind.annotation.Bindable
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Target(FIELD, VALUE_PARAMETER, ANNOTATION_CLASS)
@Retention(RUNTIME)
@Bindable //<1>
annotation class ShoppingCart(val value: String = "")
// end::class[]
