package io.micronaut.kotlin.processing.aop.introduction.delegation

import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Type

@Introduction
@Type(DelegatingInterceptor::class)
@MustBeDocumented
@Retention
annotation class DelegationAdvice
