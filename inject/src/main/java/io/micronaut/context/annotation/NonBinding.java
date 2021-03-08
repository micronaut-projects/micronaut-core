package io.micronaut.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Excludes a member of an annotation type (such as a qualifier type or interceptor binding type) from consideration when the container compares two annotation instances.
 */
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface NonBinding {
}
