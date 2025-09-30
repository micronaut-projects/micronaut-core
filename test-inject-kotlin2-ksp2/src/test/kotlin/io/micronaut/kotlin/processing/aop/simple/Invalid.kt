package io.micronaut.kotlin.processing.aop.simple

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

@Around
@Type(InvalidInterceptor::class)
@MustBeDocumented
@Retention
@Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.CLASS
)
annotation class Invalid
