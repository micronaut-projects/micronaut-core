package io.micronaut.annotation.implicitrepeat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation holding {@link Legacy} occurrences without being their declared container.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Envelope {
    Legacy[] value();
}
