package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.Around
import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Introspected

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME

@Around
@Introduction(interfaces = CustomProxy.class)
@Type(ProxyAdviceInterceptor.class)
@Documented
@Retention(RUNTIME)
@Target([ElementType.TYPE, ElementType.ANNOTATION_TYPE])
@Introspected
@Executable
@interface ProxyIntroductionAndAroundAndIntrospectedAndExecutable {
}
