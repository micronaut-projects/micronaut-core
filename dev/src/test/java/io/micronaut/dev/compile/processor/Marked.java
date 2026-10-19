package io.micronaut.dev.compile.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class the {@link MarkedProcessor} generates a companion and a resource for.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Marked {
}
