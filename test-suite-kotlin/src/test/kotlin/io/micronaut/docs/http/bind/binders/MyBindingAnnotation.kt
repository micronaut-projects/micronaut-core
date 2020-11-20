package io.micronaut.docs.http.bind.binders

// tag::class[]
import io.micronaut.core.bind.annotation.Bindable

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Bindable //<1>
annotation class MyBindingAnnotation
// end::class[]