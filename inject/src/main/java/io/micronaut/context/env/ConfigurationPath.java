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

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.function.Consumer;

/**
 * Models a configuration path such as those declared within {@link io.micronaut.context.annotation.ConfigurationProperties} and {@link io.micronaut.context.annotation.EachProperty} declarations.
 *
 * @since 4.0.0
 * @author graemerocher
 */
public sealed interface ConfigurationPath
    extends Iterable<ConfigurationPath.ConfigurationSegment>
    permits DefaultConfigurationPath {

    /**
     * @return Creates a new path.
     */
    @NonNull
    static ConfigurationPath newPath() {
        return new DefaultConfigurationPath();
    }

    /**
     * Computes the path for the given nested chain of definitions.
     * @param definitions The definitions
     * @return THe computed path
     */
    @NonNull
    static ConfigurationPath of(BeanDefinition<?>... definitions) {
        ConfigurationPath configurationPath = ConfigurationPath.newPath();
        for (BeanDefinition<?> definition : definitions) {
            if (definition.hasDeclaredAnnotation(EachProperty.class)) {
                configurationPath.pushEachPropertyRoot(definition);
            } else if (definition.hasDeclaredStereotype(ConfigurationReader.class)) {
                configurationPath.pushConfigurationReader(definition);
            }
        }
        return configurationPath;
    }

    /**
     * A configuration can have dynamic segments if it is nested within a {@link io.micronaut.context.annotation.EachProperty} instance.
     *
     * @return True if the path has any dynamic segments (ie types annotated with {@link io.micronaut.context.annotation.EachProperty}
     */
    boolean hasDynamicSegments();

    /**
     * Copy the state the of the path detaching it from any downstream mutations.
     *
     * @return The copied path
     */
    @NonNull
    ConfigurationPath copy();

    /**
     * @return The parent of the current path.
     */
    @Nullable
    ConfigurationPath parent();

    /**
     * Compute the prefix to resolve properties based on the current state of the path and the given prefix.
     *
     * @return The resolved path
     */
    @NonNull
    String prefix();

    /**
     * @return The path without segments substituted.
     */
    @NonNull
    String path();

    /**
     * @return The current primary
     */
    @Nullable
    String primary();

    /**
     * @return The current kind
     */
    @NonNull
    ConfigurationSegment.ConfigurationKind kind();

    /**
     * @return The current bound name if any.
     */
    @Nullable
    String name();

    /**
     * @return The current index or -1 if there is none
     */
    int index();

    /**
     * @return the current property catalog
     *
     * @since 4.7.0
     */
    @NonNull
    default PropertyCatalog propertyCatalog() {
        return PropertyCatalog.NORMALIZED;
    }

    /**
     * @return The qualifier.
     * @param <T> The bean type
     */
    @Nullable
    default <T> Qualifier<T> beanQualifier() {
        String n = name();
        if (n != null) {
            return Qualifiers.byName(n);
        }
        return null;
    }

    /**
     * @return Is the current binding a list.
     */
    default boolean isList() {
        ConfigurationSegment segment = peekLast();
        return segment != null && segment.kind() == ConfigurationSegment.ConfigurationKind.LIST;
    }

    /**
     * @return The last entry.
     */
    @Nullable
    ConfigurationSegment peekLast();

    /**
     * @return Is the current segment the primary.
     */
    default boolean isPrimary() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            String name = segment.name();
            return name != null && name.equals(primary());
        }
        return false;
    }

    /**
     * Push a new configuration segment for the given name and kind
     *
     * @param beanDefinition The bean definition
     */
    void pushEachPropertyRoot(@NonNull BeanDefinition<?> beanDefinition);

    /**
     * Push a new configuration segment for the given name and kind
     *
     * @param beanDefinition The bean definition
     */
    void pushConfigurationReader(@NonNull BeanDefinition<?> beanDefinition);

    /**
     * Adds a named segment.
     *
     * @param name The name of the segment
     */
    void pushConfigurationSegment(@NonNull String name);

    /**
     * Adds a indexed segment.
     *
     * @param index The index of the segment
     */
    void pushConfigurationSegment(int index);

    /**
     * remove last entry.
     *
     * @return the last element from this path
     * @throws java.util.NoSuchElementException if there isn't any remaining elements.
     */
    @NonNull ConfigurationSegment removeLast();


    /**
     * @return Whether the path is not empty.
     */
    boolean isNotEmpty();

    /**
     * Resolve the given value with the current state.
     *
     * @param value The value
     * @return The resolved value
     */
    @NonNull
    String resolveValue(String value);

    /**
     * @return The current configuration type.
     */
    @Nullable
    Class<?> configurationType();

    /**
     * @return The simple unqualified name if any.
     */
    @Nullable
    String simpleName();

    /**
     * Traverse the enabled segments for this path invoking the given callback.
     *
     * @param propertyResolver The property resolver to use.
     * @param callback         The callback.
     */
    void traverseResolvableSegments(
        @NonNull
        PropertyResolver propertyResolver,
        @NonNull
        Consumer<ConfigurationPath> callback);

    /**
     * Push and adapt an existing configuration segment.
     * @param configurationSegment The configuration segment
     */
    void pushConfigurationSegment(@NonNull ConfigurationSegment configurationSegment);

    /**
     * Check whether the given prefix is within the current path.
     * @param prefix The prefix
     * @return True if it is within the current path.
     */
    boolean isWithin(String prefix);

    /**
     * A segment of configuration.
     */
    sealed interface ConfigurationSegment extends CharSequence permits DefaultConfigurationPath.DefaultConfigurationSegment {

        /**
         * @return The prefix
         */
        String prefix();

        /**
         * @return The raw path
         */
        String path();

        /**
         * @return Whether it is a list or map binding
         */
        ConfigurationKind kind();

        /**
         * @return This name (if any)
         */
        @Nullable
        String name();

        /**
         * @return The unqualified name.
         */
        @Nullable
        String simpleName();

        /**
         * @return The primary name (if any)
         */
        @Nullable
        String primary();

        /**
         * @return The configuration type
         */
        @NonNull
        Class<?> type();

        /**
         * The current index.
         * @return -1 if there is no index
         */
        int index();

        enum ConfigurationKind {
            /**
             * A root entry.
             */
            ROOT,
            /**
             * Dynamic name.
             */
            NAME,
            /**
             * A dynamic index.
             */
            INDEX,
            /**
             * A list that requires replacement.
             */
            LIST,
            /**
             * A map that requires replacement.
             */
            MAP
        }
    }
}
