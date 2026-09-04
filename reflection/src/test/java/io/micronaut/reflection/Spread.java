package io.micronaut.reflection;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A composed contract composing {@link Sized} twice, each occurrence overridden by a member of its own through
 * the index of the occurrence.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Contract
@Sized(min = 1)
@Sized(min = 2)
public @interface Spread {

    @AliasFor(annotation = Sized.class, member = "min", index = 0)
    int first() default 1;

    @AliasFor(annotation = Sized.class, member = "min", index = 1)
    int second() default 2;
}
