package io.micronaut.inject.destroydependent;

import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@ScopedProxy
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Scope
public @interface SharedScope {
}
