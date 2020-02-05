/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.core.value;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * A property resolver is capable of resolving properties from an underlying property source.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertyResolver extends ValueResolver<String> {

    /**
     * <p>Whether the given property is contained within this resolver.</p>
     * <p>
     * <p>Note that this method will return false for nested properties. In other words given a key of <tt>foo.bar</tt> this method will
     * return <tt>false</tt> for: <code>resolver.containsProperty("foo")</code></p>
     * <p>
     * <p>To check for nested properties using {@link #containsProperties(String)} instead.</p>
     *
     * @param name The name of the property
     * @return True if it is
     */
    boolean containsProperty(@NonNull String name);

    /**
     * Whether the given property or any nested properties exist for the key given key within this resolver.
     *
     * @param name The name of the property
     * @return True if it is
     */
    boolean containsProperties(@NonNull String name);

    /**
     * <p>Resolve the given property for the given name, type and generic type arguments.</p>
     * <p>
     * <p>Implementers can choose to implement more intelligent type conversion by analyzing the typeArgument.</p>
     *
     * @param name              The name
     * @param conversionContext The conversion context
     * @param <T>               The concrete type
     * @return An optional containing the property value if it exists
     */
    @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext);

    /**
     * <p>Resolve the given property for the given name, type and generic type arguments.</p>
     * <p>
     * <p>Implementers can choose to implement more intelligent type conversion by analyzing the typeArgument.</p>
     *
     * @param name     The name
     * @param argument The required type
     * @param <T>      The concrete type
     * @return An optional containing the property value if it exists
     */
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull Argument<T> argument) {
        return getProperty(name, ConversionContext.of(argument));
    }

    /**
     * Return all the properties under the given key.
     *
     * @param name The name
     * @return The properties
     */
    default @NonNull Map<String, Object> getProperties(@NonNull String name) {
        return getProperties(name, null);
    }

    /**
     * Return all the properties under the given key. By default Micronaut stores keys in keb-case, such that normalized lookups
     * are more efficient. You can obtain the raw key values by passing in {@link StringConvention#RAW}.
     *
     * @param name The name
     * @param keyFormat The key format to use for the keys. Default is kebab-case.
     * @return The properties
     */
    default @NonNull Map<String, Object> getProperties(@Nullable String name, @Nullable StringConvention keyFormat) {
        return Collections.emptyMap();
    }

    /**
     * <p>Resolve the given property for the given name, type and generic type arguments.</p>
     * <p>
     * <p>Implementers can choose to implement more intelligent type conversion by analyzing the typeArgument.</p>
     *
     * @param name         The name
     * @param requiredType The required type
     * @param context      The {@link ConversionContext} to apply  to any conversion
     * @param <T>          The concrete type
     * @return An optional containing the property value if it exists
     */
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull Class<T> requiredType, @NonNull ConversionContext context) {
        return getProperty(name, context.with(Argument.of(requiredType)));
    }

    @Override
    default @NonNull <T> Optional<T> get(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        return getProperty(name, conversionContext);
    }

    /**
     * Resolve the given property for the given name.
     *
     * @param name         The name
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the property value if it exists
     */
    default @NonNull <T> Optional<T> getProperty(@NonNull String name, @NonNull Class<T> requiredType) {
        return getProperty(name, requiredType, ConversionContext.DEFAULT);
    }

    /**
     * Resolve the given property for the given name.
     *
     * @param name         The name
     * @param requiredType The required type
     * @param defaultValue The default value
     * @param <T>          The concrete type
     * @return An optional containing the property value if it exists
     */
    default @Nullable <T> T getProperty(@NonNull String name, @NonNull Class<T> requiredType, @Nullable T defaultValue) {
        return getProperty(name, requiredType).orElse(defaultValue);
    }

    /**
     * Resolve the given property for the given name.
     *
     * @param name         The name of the property
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return The value of the property
     * @throws PropertyNotFoundException exception when property does not exist
     */
    default @NonNull <T> T getRequiredProperty(@NonNull String name, @NonNull Class<T> requiredType) throws PropertyNotFoundException {
        return getProperty(name, requiredType).orElseThrow(() ->
            new PropertyNotFoundException(name, requiredType)
        );
    }

    /**
     * Builds a property name for the given property path.
     *
     * @param path The path
     * @return The property name
     */
    static String nameOf(String... path) {
        return String.join(".", path);
    }
}
