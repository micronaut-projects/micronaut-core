package io.micronaut.dev.compile.processor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Asks the {@link UncreditedProcessor} for a resource that no source is credited with.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Uncredited {
}
