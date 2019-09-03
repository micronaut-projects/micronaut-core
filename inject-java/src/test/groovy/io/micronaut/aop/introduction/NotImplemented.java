package io.micronaut.aop.introduction;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Introduction
@Type(NotImplementedAdvice.class)
@Documented
@Retention(RUNTIME)
public @interface NotImplemented {
}
