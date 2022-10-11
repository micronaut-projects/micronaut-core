package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME

@Around
@Type(ProxyAroundInterceptor.class)
@Documented
@Retention(RUNTIME)
@Target([ElementType.TYPE, ElementType.ANNOTATION_TYPE])
@interface ProxyAround {
}
