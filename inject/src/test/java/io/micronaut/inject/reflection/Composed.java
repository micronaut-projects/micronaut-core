package io.micronaut.inject.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation composed of two repeated {@link Tag}s, the way a custom constraint composes constraints.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Tag("first")
@Tag(value = "second", priority = 2)
public @interface Composed {
}
