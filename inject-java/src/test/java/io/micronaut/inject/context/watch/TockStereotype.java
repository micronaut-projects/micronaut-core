package io.micronaut.inject.context.watch;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Processed at startup through its {@link Tock} stereotype.
 */
@Tock
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TockStereotype {
}
