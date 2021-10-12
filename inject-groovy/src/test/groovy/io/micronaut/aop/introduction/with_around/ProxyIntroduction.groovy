package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Type

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Target

@Introduction(interfaces = CustomProxy.class)
@Type(ProxyIntroductionInterceptor.class)
@Documented
@Target([ElementType.TYPE, ElementType.ANNOTATION_TYPE])
@interface ProxyIntroduction {
}
