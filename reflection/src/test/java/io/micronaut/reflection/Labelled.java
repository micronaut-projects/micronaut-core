package io.micronaut.reflection;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation whose {@code value} aliases another member of itself, which is an alias without a target
 * annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface Labelled {

    @AliasFor(member = "name")
    String value() default "";

    @AliasFor(member = "value")
    String name() default "";
}
