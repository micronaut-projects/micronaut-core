package io.micronaut.kotlin.processing.aop.simple

import io.micronaut.aop.Around

@Around
@MustBeDocumented
@Retention
@Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.CLASS
)
annotation class TestBinding
