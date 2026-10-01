package io.micronaut.inject.scope.custom.definitionlookup;

import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A custom scope without a scoped proxy: the scope map holds the bean itself.
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Scope
public @interface LookupScope {
}
