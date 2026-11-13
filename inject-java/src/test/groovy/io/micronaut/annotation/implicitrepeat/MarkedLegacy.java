package io.micronaut.annotation.implicitrepeat;

import io.micronaut.context.annotation.Primary;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation without {@code @Repeatable} contributing a stereotype.
 */
@Primary
@Retention(RetentionPolicy.RUNTIME)
public @interface MarkedLegacy {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        MarkedLegacy[] value();
    }
}
