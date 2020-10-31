package io.micronaut.docs.http.bind.binders

import io.micronaut.core.bind.annotation.Bindable

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Bindable
annotation class MyBindingAnnotation