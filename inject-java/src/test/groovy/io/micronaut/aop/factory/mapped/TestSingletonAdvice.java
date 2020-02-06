package io.micronaut.aop.factory.mapped;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around
@Type(TestSingletonInterceptor.class)
@Documented
@Retention(RUNTIME)
public @interface TestSingletonAdvice {
}
