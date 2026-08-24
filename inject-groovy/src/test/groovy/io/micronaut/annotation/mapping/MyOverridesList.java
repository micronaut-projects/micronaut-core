package io.micronaut.annotation.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The repeatable container of {@link MyOverrides}, modelled after
 * {@code jakarta.validation.OverridesAttribute.List}.
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
public @interface MyOverridesList {

    MyOverrides[] value();
}
