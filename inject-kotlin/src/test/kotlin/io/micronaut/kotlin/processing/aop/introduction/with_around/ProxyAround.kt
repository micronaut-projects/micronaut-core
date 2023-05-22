package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

@Around
@Type(ProxyAroundInterceptor::class)
@MustBeDocumented
@Retention
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class ProxyAround
