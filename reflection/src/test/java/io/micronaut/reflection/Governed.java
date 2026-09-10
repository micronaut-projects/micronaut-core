package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The contract of a family that is not marked {@link io.micronaut.core.annotation.Retainable}, standing in for
 * one an extension does not own: {@link DerivingCustomizer} says it is retainable instead, the way a remapper
 * marks it at compilation time.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface Governed {
}

