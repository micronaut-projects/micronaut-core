/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.value;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * An interface for resolving properties from an external source.
 *
 * <p>This interface allows external property systems (such as Spring's Environment)
 * to be integrated with Micronaut's property resolution mechanism without requiring
 * subclassing of internal classes.</p>
 *
 * <p>When configured via {@link io.micronaut.context.ApplicationContextBuilder#externalPropertyResolver},
 * the external resolver becomes the <b>exclusive</b> source for property resolution.
 * Micronaut's internal property catalog is bypassed entirely, making the external system
 * the sole authority for all property lookups.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationContext.builder()
 *     .externalPropertyResolver(new ExternalPropertyResolver() {
 *         public <T> Optional<T> resolve(String name, ArgumentConversionContext<T> context) {
 *             return Optional.ofNullable(externalSource.getProperty(name, context.getArgument().getType()));
 *         }
 *
 *         public boolean contains(String name) {
 *             return externalSource.containsProperty(name);
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * @author Micronaut Team
 * @since 5.0
 * @see io.micronaut.context.ApplicationContextBuilder#externalPropertyResolver
 */
@NullMarked
@Experimental
public interface ExternalPropertyResolver {

    /**
     * Resolve a property value from the external source.
     *
     * @param name The property name
     * @param conversionContext The conversion context containing type information
     * @param <T> The expected property type
     * @return An optional containing the property value if found, empty otherwise
     */
    @NonNull
    <T> Optional<T> resolve(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext);

    /**
     * Check if a property exists in the external source.
     *
     * <p>The default implementation calls {@link #resolve} and checks if the result is present.</p>
     *
     * @param name The property name
     * @return true if the property exists in the external source, false otherwise
     */
    default boolean contains(@NonNull String name) {
        return resolve(name, ConversionContext.STRING).isPresent();
    }

    /**
     * Check if nested properties exist under the given prefix in the external source.
     *
     * <p>The default implementation delegates to {@link #contains}.</p>
     *
     * @param name The property prefix
     * @return true if any properties exist under this prefix, false otherwise
     */
    default boolean containsProperties(@NonNull String name) {
        return contains(name);
    }
}
