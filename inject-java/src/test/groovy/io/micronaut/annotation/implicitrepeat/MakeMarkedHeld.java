package io.micronaut.annotation.implicitrepeat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Test annotation for a declared container supplied as a stereotype without the stereotypes of its occurrences.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface MakeMarkedHeld {
}
