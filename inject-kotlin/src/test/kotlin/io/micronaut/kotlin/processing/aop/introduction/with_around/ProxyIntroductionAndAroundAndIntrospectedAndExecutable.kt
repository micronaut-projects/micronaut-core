package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.Around
import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Type
import io.micronaut.core.annotation.Introspected

@Around(proxyTarget = true)
@Introduction(interfaces = [CustomProxy::class])
@Type(ProxyAdviceInterceptor::class)
@MustBeDocumented
@Retention
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
@Introspected
@Executable
annotation class ProxyIntroductionAndAroundAndIntrospectedAndExecutable
