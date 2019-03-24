package io.micronaut.validation.validator.extractors;

import javax.annotation.Nonnull;
import javax.validation.ValidationException;
import javax.validation.valueextraction.ValueExtractor;
import java.util.Optional;

/**
 * Registry of value extractors.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ValueExtractorRegistry {
    /**
     * Finds a a {@link ValueExtractor} for the given type.
     * @param targetType The target type of the value
     * @param <T> The target type
     * @return The extractor
     */
    @Nonnull
    <T> Optional<ValueExtractor<T>> findValueExtractor(
            @Nonnull Class<T> targetType);

    /**
     * Finds a concrete {@link ValueExtractor} without searching the hierarchy.
     * @param targetType The target type of the value
     * @param <T> The target type
     * @return The extractor
     */
    @Nonnull
    <T> Optional<ValueExtractor<T>> findConcreteExtractor(
            @Nonnull Class<T> targetType);

    /**
     * Gets a a {@link ValueExtractor} for the given type.
     * @param targetType The target type of the value
     * @param <T> The target type
     * @return The extractor
     * @throws ValidationException if no extractor is present
     */
    @Nonnull
    default <T> ValueExtractor<T> getValueExtractor(
            @Nonnull Class<T> targetType) {
        return findValueExtractor(targetType)
                .orElseThrow(() -> new ValidationException("No value extractor for target type [" + targetType + "]"));
    }
}
