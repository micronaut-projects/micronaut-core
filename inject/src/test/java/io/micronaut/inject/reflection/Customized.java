package io.micronaut.inject.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation a {@link io.micronaut.inject.annotation.ReflectionAnnotationCustomizer} derives a member of.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface Customized {

    String value() default "";

    String derived() default "";
}
