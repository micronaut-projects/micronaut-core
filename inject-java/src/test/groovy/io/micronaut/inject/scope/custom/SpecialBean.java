package io.micronaut.inject.scope.custom;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import jakarta.inject.Scope;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Scope
public @interface SpecialBean {}