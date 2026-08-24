package io.micronaut.annotation.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation without any Micronaut dependency, modelled after {@code jakarta.validation.OverridesAttribute},
 * that is transformed to {@code @AliasFor} by a registered {@code AnnotationTransformer}.
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
public @interface MyOverrides {

    Class<?> constraint();

    String name() default "";
}
