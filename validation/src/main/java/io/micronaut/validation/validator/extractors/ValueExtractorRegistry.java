/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.validator.extractors;

import edu.umd.cs.findbugs.annotations.NonNull;
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
    @NonNull
    <T> Optional<ValueExtractor<T>> findValueExtractor(
            @NonNull Class<T> targetType);

    /**
     * Finds a concrete {@link ValueExtractor} without searching the hierarchy.
     * @param targetType The target type of the value
     * @param <T> The target type
     * @return The extractor
     */
    @NonNull
    <T> Optional<ValueExtractor<T>> findUnwrapValueExtractor(
            @NonNull Class<T> targetType);

    /**
     * Gets a a {@link ValueExtractor} for the given type.
     * @param targetType The target type of the value
     * @param <T> The target type
     * @return The extractor
     * @throws ValidationException if no extractor is present
     */
    @NonNull
    default <T> ValueExtractor<T> getValueExtractor(
            @NonNull Class<T> targetType) {
        return findValueExtractor(targetType)
                .orElseThrow(() -> new ValidationException("No value extractor for target type [" + targetType + "]"));
    }
}
