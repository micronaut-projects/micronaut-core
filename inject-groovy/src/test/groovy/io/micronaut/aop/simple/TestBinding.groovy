package io.micronaut.aop.simple;

import io.micronaut.aop.Around;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around
@Documented
@Retention(RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@interface TestBinding {
}
