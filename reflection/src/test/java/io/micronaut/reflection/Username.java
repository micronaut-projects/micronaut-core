package io.micronaut.reflection;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A composed contract: it composes {@link Sized} and overrides its {@code min} through {@link AliasFor}, the
 * way a composed constraint overrides a member of the constraint it composes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Contract
@Sized(min = 5)
public @interface Username {

    @AliasFor(annotation = Sized.class, member = "min")
    int min() default 5;
}
