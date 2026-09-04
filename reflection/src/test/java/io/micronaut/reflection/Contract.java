package io.micronaut.reflection;

import io.micronaut.core.annotation.Retainable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A retainable contract: every annotation composing one of its members keeps that occurrence in its own
 * retained tree, the way {@code jakarta.validation.Constraint} does.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retainable
public @interface Contract {
    Class<?>[] handledBy() default {};
}
