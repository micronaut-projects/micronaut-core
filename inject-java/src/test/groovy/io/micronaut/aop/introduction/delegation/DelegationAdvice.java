package io.micronaut.aop.introduction.delegation;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Introduction
@Type(DelegatingInterceptor.class)
@Documented
@Retention(RUNTIME)
public @interface DelegationAdvice {
}
