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
 * A lazy proxy that keeps the target it resolved, which no scope holds.
 */
@Around(lazy = true, proxyTarget = true, cacheableLazyTarget = true)
@ScopedProxy
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Scope
public @interface CachedLazy {
}
