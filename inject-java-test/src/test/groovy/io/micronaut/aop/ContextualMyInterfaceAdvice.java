package io.micronaut.aop;

import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Introduction
@Type(ContextualMyInterfaceInterceptor.class)
@Internal
@interface ContextualMyInterfaceAdvice {
}
