package io.micronaut.aop.around.proxytarget;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true)
@Type(ArgMutatingInterceptor.class)
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Mutating {
    String value();
}


