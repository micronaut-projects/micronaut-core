package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A record whose component carries an annotation that can only land on the accessor.
 */
public record Coordinate(@Coordinate.Axis("x") String label, int value) {

    /**
     * An annotation a record component can only put on its accessor.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Axis {

        String value();
    }
}
