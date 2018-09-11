/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    default String getPrefix() {
        return DefaultPropertyPlaceholderResolver.PREFIX;
    }

    /**
     * Resolve the placeholders and return an Optional String if it was possible to resolve them.
     *
     * @param str The placeholder to resolve
     * @return The optional string or {@link Optional#empty()} if resolution was not possible
     * @throws ConfigurationException If the placeholders could not be resolved
     */
    default String resolveRequiredPlaceholders(String str) throws ConfigurationException {
        return resolvePlaceholders(str).orElseThrow(() -> new ConfigurationException("Unable to resolve placeholders for property: " + str));
    }

    /**
     * Resolves all the property names defined in the given place holder string.
     *
     * @param str The string
     * @return a list of property names
     */
    default List<Placeholder> resolvePropertyNames(String str) {
        try {
            String prefix = getPrefix();
            if (StringUtils.isNotEmpty(str)) {
                int i = str.indexOf(prefix);

                if (i != -1) {
                    List<Placeholder> placeholders = new ArrayList<>(3);
                    String restOfString = str.substring(i + 2);
                    while (i != -1) {
                        int e = restOfString.indexOf('}');
                        if (e > -1) {
                            String expr = restOfString.substring(0, e).trim();
                            int j = expr.indexOf(':');

                            if (j == -1) {
                                placeholders.add(new DefaultPlaceholder(expr, null));
                            } else {
                                String defaultValue = expr.substring(j + 1);
                                expr = expr.substring(0, j);
                                placeholders.add(new DefaultPlaceholder(expr, defaultValue));
                            }

                            i = restOfString.indexOf(prefix);
                            if (i != -1) {
                                restOfString = restOfString.substring(i + 2);
                            }
                        } else {
                            // incomplete place holder
                            return Collections.emptyList();
                        }
                    }
                    return placeholders;
                }
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /**
     * A place holder definition.
     */
    interface Placeholder {
        /**
         * The property.
         *
         * @return The property
         */
        String getProperty();

        /**
         * The default value.
         *
         * @return The default value
         */
        Optional<String> getDefaultValue();
    }
}
