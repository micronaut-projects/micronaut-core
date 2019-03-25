package io.micronaut.validation.validator.extractors;

import javax.validation.valueextraction.ValueExtractor;

/**
 * Interface based alternative for unwrap by default semantics.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface UnwrapByDefaultValueExtractor<T> extends ValueExtractor<T> {
}
