/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.env;

import io.micronaut.core.order.Ordered;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A PropertySource is a location to resolve property values from. The property keys are are available via the
 * {@link #iterator()} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySource extends Iterable<String>, Ordered {

    /**
     * The name of the property source with values supplied directly from the context.
     */
    String CONTEXT = "context";

    /**
     * @return The name of the property source
     */
    String getName();

    /**
     * Get a property value of the given key.
     *
     * @param key The key
     * @return The value
     */
    Object get(String key);

    /**
     * @return Whether the property source has upper case under score separated keys
     */
    default PropertyConvention getConvention() {
        return PropertyConvention.JAVA_PROPERTIES;
    }

    /**
     * Create a {@link PropertySource} from the given map.
     *
     * @param name The name of the property source
     * @param map  The map
     * @return The {@link PropertySource}
     */
    static PropertySource of(String name, Map<String, Object> map) {
        return new MapPropertySource(name, map);
    }

    /**
     * Create a {@link PropertySource} from the given map.
     *
     * @param name       The name of the property source
     * @param map        The map
     * @param convention The convention type of the property source
     * @return The {@link PropertySource}
     */
    static PropertySource of(String name, Map<String, Object> map, PropertyConvention convention) {
        return new MapPropertySource(name, map) {
            @Override
            public PropertyConvention getConvention() {
                return convention;
            }
        };
    }

    /**
     * Create a {@link PropertySource} from the given map.
     *
     * @param name The name of the property source
     * @param values The values as an array of alternating key/value entries
     * @return The {@link PropertySource}
     * @since 2.0
     */
    static PropertySource of(String name, Object... values) {
        return new MapPropertySource(name, mapOf(values));
    }

    /**
     * Create a {@link LinkedHashMap} of configuration from an array of values.
     *
     * @param values The values
     * @return The created map
     * @since 2.0
     */
    static Map<String, Object> mapOf(Object... values) {
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        Map<String, Object> answer = new LinkedHashMap<>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            Object k = values[i++];
            if (k != null) {
                answer.put(k.toString(), values[i++]);
            }
        }
        return answer;
    }

    /**
     * Create a {@link PropertySource} from the given map.
     *
     * @param name     The name of the property source
     * @param map      The map
     * @param priority The priority to order by
     * @return The {@link PropertySource}
     */
    static PropertySource of(String name, Map<String, Object> map, int priority) {
        return new MapPropertySource(name, map) {
            @Override
            public int getOrder() {
                return priority;
            }
        };
    }

    /**
     * Create a {@link PropertySource} named {@link Environment#DEFAULT_NAME} from the given map.
     *
     * @param map The map
     * @return The {@link PropertySource}
     */
    static PropertySource of(Map<String, Object> map) {
        return new MapPropertySource(Environment.DEFAULT_NAME, map);
    }

    /**
     * Property convention.
     */
    enum PropertyConvention {

        /**
         * Upper case separated by under scores (environment variable style).
         */
        ENVIRONMENT_VARIABLE,

        /**
         * Lower case separated by dots (java properties file style).
         */
        JAVA_PROPERTIES
    }
}
