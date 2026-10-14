package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation meta-annotated with plain {@link Executable}, as a messaging module's topic annotation is, so that its
 * processor is deprecated and fed by the legacy listener as well as by the startup pass.
 */
@Executable
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Buzz {
}
