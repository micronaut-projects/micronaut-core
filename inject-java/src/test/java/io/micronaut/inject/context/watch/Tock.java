package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Processed at startup, and usable as a stereotype.
 */
@Executable(processOnStartup = true)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface Tock {
}
