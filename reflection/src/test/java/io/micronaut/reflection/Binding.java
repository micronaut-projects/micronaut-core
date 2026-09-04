package io.micronaut.reflection;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A qualifier with a non-binding member.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Binding {

    String value();

    @NonBinding
    String comment() default "";
}
