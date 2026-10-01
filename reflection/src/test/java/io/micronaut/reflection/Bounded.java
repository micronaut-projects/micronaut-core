package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A member of the {@link Governed} family, composed by {@link Ranged}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Governed
@Repeatable(Bounds.class)
public @interface Bounded {
    int least() default 0;
}
