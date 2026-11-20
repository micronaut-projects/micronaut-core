package io.micronaut.annotation.implicitrepeat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A class-retained annotation without {@code @Repeatable}, held in a runtime container.
 */
@Retention(RetentionPolicy.CLASS)
public @interface ClassLegacy {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @ImplicitContainer
    @interface List {
        ClassLegacy[] value();
    }
}
