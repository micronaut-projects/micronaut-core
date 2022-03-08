package io.micronaut.kotlin.processing.aop.proxytarget

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

@Around(proxyTarget = true)
@Type(ArgMutatingInterceptor::class)
@MustBeDocumented
@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Mutating(val value: String)


