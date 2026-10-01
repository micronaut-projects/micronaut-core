package io.micronaut.reflection;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A composed member of the {@link Governed} family composing {@link Bounded} twice, each occurrence overridden
 * by a member of its own through the index of the occurrence.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Governed
@Bounded(least = 1)
@Bounded(least = 2)
public @interface Spanned {

    @AliasFor(annotation = Bounded.class, member = "least", index = 0)
    int first() default 1;

    @AliasFor(annotation = Bounded.class, member = "least", index = 1)
    int second() default 2;
}
