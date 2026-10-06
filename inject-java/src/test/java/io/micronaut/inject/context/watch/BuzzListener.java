package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose methods are processed at startup, as a messaging module's listener annotation does.
 */
@Executable(processOnStartup = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BuzzListener {
}
