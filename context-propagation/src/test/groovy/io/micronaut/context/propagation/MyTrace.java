package io.micronaut.context.propagation;

import io.micronaut.aop.Around;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@interface MyTrace {
}


