package io.micronaut.inject.scope.custom.perbean;

import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A scope whose beans are created under a lock per bean identifier.
 */
@Documented
@Retention(RUNTIME)
@Scope
public @interface PerBeanScope {
}
