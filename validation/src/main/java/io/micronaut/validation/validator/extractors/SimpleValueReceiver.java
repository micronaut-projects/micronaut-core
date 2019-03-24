package io.micronaut.validation.validator.extractors;

import javax.validation.valueextraction.ValueExtractor;

/**
 * No-op implementation that makes it easier to use with Lambdas.
 *
 * @author graemerocher
 * @since 1.2
 */
@FunctionalInterface
public interface SimpleValueReceiver extends ValueExtractor.ValueReceiver {
    @Override
    default void iterableValue(String nodeName, Object object) {

    }

    @Override
    default void indexedValue(String nodeName, int i, Object object) {

    }

    @Override
    default void keyedValue(String nodeName, Object key, Object object) {

    }
}
