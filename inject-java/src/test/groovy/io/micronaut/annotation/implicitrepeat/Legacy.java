package io.micronaut.annotation.implicitrepeat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation without {@code @Repeatable}, repeated in its nested container the way constraints predating it are.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Legacy {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @ImplicitContainer
    @interface List {
        Legacy[] value();
    }
}
