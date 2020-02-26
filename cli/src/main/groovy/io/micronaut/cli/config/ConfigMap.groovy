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
package io.micronaut.cli.config

/**
 * API onto application configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface ConfigMap extends Iterable<Map.Entry<String, Object>>, Map<String, Object> {

    /**
     * Enables the object[foo] syntax
     *
     * @param key The key
     * @return The value or null
     */
    def getAt(Object key)
    /**
     * Enables the object[foo] = 'stuff' syntax
     *
     * @param key The key
     * @param value The value
     */
    void setAt(Object key, Object value)

    /**
     * Return the property value associated with the given key, or {@code null}
     * if the key cannot be resolved.
     * @param key the property name to resolve
     * @param targetType the expected type of the property value
     * @see #getRequiredProperty(String, Class)
     */
    def <T> T getProperty(String key, Class<T> targetType)

    /**
     * Return the property value associated with the given key, or {@code null}
     * if the key cannot be resolved.
     * @param key the property name to resolve
     * @param targetType the expected type of the property value
     * @see #getRequiredProperty(String, Class)
     */
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue)

    /**
     * Return the property value associated with the given key, converted to the given
     * targetType (never {@code null}).
     * @throws IllegalStateException if the given key cannot be resolved
     */
    def <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException

    /**
     * Navigate the map for the given path
     *
     * @param path The path
     * @return
     */
    Object navigate(String... path)
}