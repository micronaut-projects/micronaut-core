package io.micronaut.inject.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation without {@code @Repeatable}, stored directly by metadata compiled before its container was declared.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface OldStyle {
    String value();
}
