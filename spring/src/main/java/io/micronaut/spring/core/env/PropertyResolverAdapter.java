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
package io.micronaut.spring.core.env;

import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import org.springframework.core.env.PropertyResolver;

import java.util.Optional;

/**
 * Adapts a {@link io.micronaut.core.value.PropertyResolver} to a Spring {@link org.springframework.core.env.PropertyResolver}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertyResolverAdapter implements PropertyResolver {

    private final io.micronaut.core.value.PropertyResolver propertyResolver;
    private final PropertyPlaceholderResolver placeholderResolver;

    /**
     * Constructor.
     *
     * @param propertyResolver The property resolver
     * @param placeholderResolver The property placeholder resolver
     */
    public PropertyResolverAdapter(io.micronaut.core.value.PropertyResolver propertyResolver, PropertyPlaceholderResolver placeholderResolver) {
        this.propertyResolver = propertyResolver;
        this.placeholderResolver = placeholderResolver;
    }

    /**
     * @return The micronaut property resolver
     */
    public io.micronaut.core.value.PropertyResolver getPropertyResolver() {
        return propertyResolver;
    }

    @Override
    public boolean containsProperty(String key) {
        return propertyResolver.getProperty(NameUtils.hyphenate(key), String.class).isPresent();
    }

    @Override
    public String getProperty(String key) {
        return propertyResolver.getProperty(NameUtils.hyphenate(key), String.class).orElse(null);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return getProperty(NameUtils.hyphenate(key), String.class, null);
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType) {
        return getProperty(NameUtils.hyphenate(key), targetType, null);
    }

    @Override
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return propertyResolver.getProperty(NameUtils.hyphenate(key), targetType, defaultValue);
    }

    /**
     * Return the property value converted to a class loaded by
     * the current thread context class loader.
     *
     * @param key The property key
     * @param targetType The class
     * @param <T> The class type
     * @return The class value
     */
    @Deprecated
    public <T> Class<T> getPropertyAsClass(String key, Class<T> targetType) {
        Optional<String> property = propertyResolver.getProperty(NameUtils.hyphenate(key), String.class);
        if (property.isPresent()) {
            Optional<Class> aClass = ClassUtils.forName(property.get(), Thread.currentThread().getContextClassLoader());
            if (aClass.isPresent()) {
                //noinspection unchecked
                return aClass.get();
            }
        }
        return null;
    }

    @Override
    public String getRequiredProperty(String key) throws IllegalStateException {
        return getRequiredProperty(NameUtils.hyphenate(key), String.class);
    }

    @Override
    public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        T v = getProperty(NameUtils.hyphenate(key), targetType, null);
        if (v == null) {
            throw new IllegalStateException("Property [" + key + "] not found");
        }
        return v;
    }

    @Override
    public String resolvePlaceholders(String text) {
        return placeholderResolver.resolvePlaceholders(text).orElse(null);
    }

    @Override
    public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        return placeholderResolver.resolvePlaceholders(text).orElseThrow(() -> new IllegalArgumentException("Unable to resolve placeholders for property: " + text));
    }
}
