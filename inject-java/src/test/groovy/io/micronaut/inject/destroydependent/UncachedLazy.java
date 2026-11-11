package io.micronaut.inject.destroydependent;

import io.micronaut.aop.Around;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A lazy proxy that resolves a new target on every call.
 */
@Around(lazy = true, proxyTarget = true)
@ScopedProxy
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Scope
public @interface UncachedLazy {
}
