package io.micronaut.validation.validator.extractors;

import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;
import javax.validation.valueextraction.ValueExtractor;
import java.util.*;

/**
 * The default value extractors.
 *
 * @author graemerocher
 * @since 1.2
 */
@Factory
public class DefaultValueExtractors {

    public static final String RETURN_VALUE_NODE_NAME = "<return value>";
    public static final String CROSS_PARAMETER_NODE_NAME = "<cross-parameter>";
    public static final String ITERABLE_ELEMENT_NODE_NAME = "<iterable element>";
    public static final String LIST_ELEMENT_NODE_NAME = "<list element>";
    public static final String MAP_KEY_NODE_NAME = "<map key>";
    public static final String MAP_VALUE_NODE_NAME = "<map value>";

    /**
     * Value extractor for optional.
     *
     * @return The value extractor.
     */
    @Singleton
    public ValueExtractor<Optional> optionalValueExtractor() {
        return (originalValue, receiver) -> receiver.value(null, originalValue.orElse(null));
    }

    /**
     * Value extractor for {@link OptionalInt}.
     *
     * @return The value extractor
     */
    @Singleton
    public ValueExtractor<OptionalInt> optionalIntValueExtractor() {
        return (originalValue, receiver) ->
                receiver.value(null, originalValue.isPresent() ? originalValue.getAsInt() : null);
    }

    /**
     * Value extractor for {@link OptionalLong}.
     *
     * @return The value extractor
     */
    @Singleton
    public ValueExtractor<OptionalLong> optionalLongValueExtractor() {
        return (originalValue, receiver) ->
                receiver.value(null, originalValue.isPresent() ? originalValue.getAsLong() : null);
    }

    /**
     * Value extractor for {@link OptionalDouble}.
     *
     * @return The value extractor
     */
    @Singleton
    public ValueExtractor<OptionalDouble> optionalDoubleValueExtractor() {
        return (originalValue, receiver) ->
                receiver.value(null, originalValue.isPresent() ? originalValue.getAsDouble() : null);
    }

    /**
     * Value extractor for iterable.
     * @return The value extractor
     */
    @Singleton
    public ValueExtractor<Iterable> iterableValueExtractor() {
        return (originalValue, receiver) -> {
            if (originalValue instanceof List) {
                int i = 0;
                for (Object o : originalValue) {
                    receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i++, o);
                }
            } else {
                for (Object o : originalValue) {
                    receiver.iterableValue(ITERABLE_ELEMENT_NODE_NAME, o);
                }
            }
        };
    }

    /**
     * Value extractor for iterable.
     * @return The value extractor
     */
    @Singleton
    public ValueExtractor<Map<?, ?>> mapValueExtractor() {
        return (originalValue, receiver) -> {
            for (Map.Entry<?, ?> entry : originalValue.entrySet()) {
                receiver.keyedValue(MAP_VALUE_NODE_NAME, entry.getKey(), entry.getValue());
            }
        };
    }

    /**
     * Value extractor for Object[].
     * @return The object[] instructor
     */
    @Singleton
    public ValueExtractor<Object[]> objectArrayValueExtractor() {
        return (originalValue, receiver) -> {
            for (int i = 0; i < originalValue.length; i++) {
                receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
            }
        };
    }
}
