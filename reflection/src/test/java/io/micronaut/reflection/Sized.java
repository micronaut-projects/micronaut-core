package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A contract member, composed by {@link Username}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Contract
@Repeatable(Sizes.class)
public @interface Sized {
    int min() default 0;

    int max() default Integer.MAX_VALUE;

    String message() default "size";
}
