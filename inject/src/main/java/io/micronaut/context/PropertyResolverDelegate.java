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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.value.ValueResolver;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The PropertyResolverDelegate interface acts as a wrapper or intermediary for a {@link PropertyResolver},
 * delegating all method calls to the underlying implementation. It combines functionality from the
 * {@link PropertyResolver} and {@link ValueResolver} interfaces.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public interface PropertyResolverDelegate extends PropertyResolver, ValueResolver<String> {

    /**
     * Returns the delegated {@link PropertyResolver} instance.
     *
     * @return the delegate {@link PropertyResolver}
     */
    PropertyResolver delegate();

    @Override
    default boolean containsProperty(@NonNull String name) {
        return delegate().containsProperty(name);
    }

    @Override
    default boolean containsProperties(@NonNull String name) {
        return delegate().containsProperties(name);
    }

    @Override
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        return delegate().getProperty(name, conversionContext);
    }

    @Override
    default @NonNull Collection<String> getPropertyEntries(@NonNull String name) {
        return delegate().getPropertyEntries(name);
    }

    @Override
    default @NonNull Collection<String> getPropertyEntries(@NonNull String name, @NonNull PropertyCatalog propertyCatalog) {
        return delegate().getPropertyEntries(name, propertyCatalog);
    }

    @Override
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull Argument<T> argument) {
        return delegate().getProperty(name, argument);
    }

    @Override
    default @NonNull Map<String, Object> getProperties(@NonNull String name) {
        return delegate().getProperties(name);
    }

    @Override
    default @NonNull Map<String, Object> getProperties(@Nullable String name, @Nullable StringConvention keyFormat) {
        return delegate().getProperties(name, keyFormat);
    }

    @Override
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull Class<T> requiredType, @NonNull ConversionContext context) {
        return delegate().getProperty(name, requiredType, context);
    }

    @Override
    default @NonNull <T> Optional<T> get(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        return delegate().get(name, conversionContext);
    }

    @Override
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull Class<T> requiredType) {
        return delegate().getProperty(name, requiredType);
    }

    @Override
    default @Nullable <T> T getProperty(@NonNull String name, @NonNull Class<T> requiredType, @Nullable T defaultValue) {
        return delegate().getProperty(name, requiredType, defaultValue);
    }

    @Override
    default @NonNull <T> T getRequiredProperty(@NonNull String name, @NonNull Class<T> requiredType) {
        return delegate().getRequiredProperty(name, requiredType);
    }

    @Override
    default @NonNull Collection<List<String>> getPropertyPathMatches(@NonNull String pathPattern) {
        return delegate().getPropertyPathMatches(pathPattern);
    }

    @Override
    default @NonNull <T> Optional<T> get(@NonNull String name, @NonNull Class<T> requiredType) {
        return delegate().get(name, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> get(@NonNull String name, @NonNull Argument<T> requiredType) {
        return delegate().get(name, requiredType);
    }

    @Override
    default <T> T get(@NonNull String name, @NonNull Class<T> requiredType, T defaultValue) {
        return delegate().get(name, requiredType, defaultValue);
    }
}



