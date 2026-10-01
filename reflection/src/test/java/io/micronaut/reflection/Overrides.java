package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An override declared in the terms of a specification rather than through {@code @AliasFor}, the way
 * {@code jakarta.validation.OverridesAttribute} declares one: {@link DerivingCustomizer} maps it, the way a
 * transformer maps it at compilation time.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Overrides {
    Class<?> annotation();

    String member();
}
