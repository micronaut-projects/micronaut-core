package io.micronaut.inject.any.qualifier;

import jakarta.inject.Scope;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Scope
public @interface AnyPerBeanScope {
}
