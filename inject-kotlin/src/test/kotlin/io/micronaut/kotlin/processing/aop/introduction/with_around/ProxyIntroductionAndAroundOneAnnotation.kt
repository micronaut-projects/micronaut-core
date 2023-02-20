package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.Around
import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Type

@Around
@Introduction(interfaces = [CustomProxy::class])
@Type(ProxyAdviceInterceptor::class)
@MustBeDocumented
@Retention
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class ProxyIntroductionAndAroundOneAnnotation
