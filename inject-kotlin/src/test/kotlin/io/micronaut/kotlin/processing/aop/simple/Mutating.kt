package io.micronaut.kotlin.processing.aop.simple

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type
import java.lang.annotation.Inherited

@Around
@Type(ArgMutatingInterceptor::class)
@MustBeDocumented
@Retention
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD
)
@Inherited
annotation class Mutating(val value: String)
