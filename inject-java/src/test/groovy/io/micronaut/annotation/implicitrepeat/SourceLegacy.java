package io.micronaut.annotation.implicitrepeat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A source-only annotation without {@code @Repeatable}, held in a runtime container.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface SourceLegacy {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        SourceLegacy[] value();
    }
}
