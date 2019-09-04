package io.micronaut.aop;

import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around
@Type(LoggedInterceptor.class)
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Logged {
}
