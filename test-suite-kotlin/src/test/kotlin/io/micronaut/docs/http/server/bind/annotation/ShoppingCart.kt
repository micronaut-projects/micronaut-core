package io.micronaut.docs.http.server.bind.annotation

// tag::class[]
import io.micronaut.core.bind.annotation.Bindable

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Bindable //<1>
annotation class ShoppingCart(val value: String = "")
// end::class[]
