package io.micronaut.dev.compile.processor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a class for {@link AggregatedProcessor}.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Aggregated {
}
