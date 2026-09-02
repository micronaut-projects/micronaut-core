package io.micronaut.inject.scope.runtime;

import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A scope whose {@link io.micronaut.context.scope.CustomScope} is only registered at runtime.
 */
@Documented
@Retention(RUNTIME)
@Scope
public @interface RuntimeRegistered {
}
