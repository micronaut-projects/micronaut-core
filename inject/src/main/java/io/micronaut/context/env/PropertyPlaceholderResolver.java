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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Interface for implementations that resolve placeholders in configuration and annotations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertyPlaceholderResolver {

    /**
     * Resolve the placeholders and return an Optional String if it was possible to resolve them.
     *
     * @param str The placeholder to resolve
     * @return The optional string or {@link Optional#empty()} if resolution was not possible
     */
    Optional<String> resolvePlaceholders(String str);

    /**
     * @return The prefix used
     */
    default @Nonnull String getPrefix() {
        return DefaultPropertyPlaceholderResolver.PREFIX;
    }

    /**
     * Resolve the placeholders and return an Optional String if it was possible to resolve them.
     *
     * @param str The placeholder to resolve
     * @return The optional string or {@link Optional#empty()} if resolution was not possible
     * @throws ConfigurationException If the placeholders could not be resolved
     */
    default @Nonnull String resolveRequiredPlaceholders(String str) throws ConfigurationException {
        return resolvePlaceholders(str).orElseThrow(() -> new ConfigurationException("Unable to resolve placeholders for property: " + str));
    }

    /**
     * Resolves the value of a single placeholder.
     *
     * @param str The string containing the placeholder
     * @param type The class of the type
     * @param <T> The type the value should be converted to
     * @return The resolved value
     * @throws ConfigurationException If multiple placeholders are found or
     * if the placeholder could not be converted to the requested type
     */
    default @Nonnull <T> T resolveRequiredPlaceholder(String str, Class<T> type) throws ConfigurationException {
        throw new ConfigurationException("Unsupported operation");
    }
}
