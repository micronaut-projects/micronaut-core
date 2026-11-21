package io.micronaut.inject.context.dependencies.application;

import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A scope whose lookups let a test run something in the middle of a recreation, as another thread would.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Scope
public @interface Interleaved {
}
