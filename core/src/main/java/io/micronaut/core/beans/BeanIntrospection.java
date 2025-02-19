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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import io.micronaut.core.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link BeanIntrospection} is the result of compile time computation of a beans properties and annotation metadata.
 *
 * <p>This interface allows you to instantiate and read and write to bean properties without using reflection or caching reflective metadata, which is
 * expensive from a memory consumption perspective.</p>
 *
 * <p>{@link BeanIntrospection} instances can be obtained either via {@link #getIntrospection(Class)} or via the {@link BeanIntrospector}.</p>
 *
 * <p>A {@link BeanIntrospection} is only computed at compilation time if the class is annotated with {@link io.micronaut.core.annotation.Introspected}. </p>
 *
 * @param <T> The bean type
 * @author graemerocher
 * @see BeanIntrospector
 * @see io.micronaut.core.annotation.Introspected
 * @since 1.1
 */
public interface BeanIntrospection<T> extends AnnotationMetadataDelegate, BeanInfo<T> {

    /**
     * @return An immutable collection of properties.
     */
    @NonNull Collection<BeanProperty<T, Object>> getBeanProperties();

    /**
     * @return An immutable collection of read properties.
     * @since 4.4.0
     */
    @NonNull
    default List<BeanReadProperty<T, Object>> getBeanReadProperties() {
        return List.of();
    }

    /**
     * @return An immutable collection of write properties.
     * @since 4.4.0
     */
    @NonNull
    default List<BeanWriteProperty<T, Object>> getBeanWriteProperties() {
        return List.of();
    }

    /**
     * Get all the bean properties annotated for the given annotation type. If the annotation is {@link io.micronaut.core.annotation.Introspected#indexed()} by the given annotation,
     * then it will be included in the resulting list.
     *
     * @param annotationType The annotation type
     * @return An immutable collection of properties.
     * @see io.micronaut.core.annotation.Introspected#indexed()
     */
    @NonNull Collection<BeanProperty<T, Object>> getIndexedProperties(@NonNull Class<? extends Annotation> annotationType);

    /**
     * @return Obtain a builder for this introspection.
     * @since 4.1.0
     */
    @NonNull Builder<T> builder();

    /**
     * Can the introspection be constructed with {@link #builder()}.
     * @return True if it can
     * @since 4.1.0
     */
    default boolean isBuildable() {
        return false;
    }

    /**
     * Does the introspection have a backing builder.
     *
     * <p>This method exists so that consumers can choose to optimize towards direct instantiation using {@link #instantiate()} if no builder is present.</p>
     *
     * @return True if a backing builder is present.
     * @since 4.1.0
     */
    default boolean hasBuilder() {
        return false;
    }

    /**
     * Instantiates an instance of the bean, throwing an exception is instantiation is not possible.
     *
     * @return An instance
     * @throws InstantiationException If the bean cannot be instantiated or the arguments are not satisfied.
     */
    @NonNull T instantiate() throws InstantiationException;

    /**
     * Instantiates an instance of the bean, throwing an exception is instantiation is not possible.
     *
     * @param arguments The arguments required to instantiate bean. Should match the types returned by {@link #getConstructorArguments()}
     * @return An instance
     * @throws InstantiationException If the bean cannot be instantiated.
     */
    default @NonNull T instantiate(Object... arguments) throws InstantiationException {
        return instantiate(true, arguments);
    }

    /**
     * Instantiates an instance of the bean, throwing an exception is instantiation is not possible.
     *
     * @param strictNullable If true, require null parameters to be annotated with a nullable annotation
     * @param arguments      The arguments required to instantiate bean. Should match the types returned by {@link #getConstructorArguments()}
     * @return An instance
     * @throws InstantiationException If the bean cannot be instantiated.
     */
    @NonNull T instantiate(boolean strictNullable, Object... arguments) throws InstantiationException;

    /**
     * The bean type.
     *
     * @return The bean type
     */
    @Override
    @NonNull Class<T> getBeanType();

    /**
     * Get all the bean properties annotated for the given type.
     *
     * @param annotationType  The annotation type
     * @param annotationValue The annotation value
     * @return An immutable collection of properties.
     * @see io.micronaut.core.annotation.Introspected#indexed()
     */
    @NonNull Optional<BeanProperty<T, Object>> getIndexedProperty(
        @NonNull Class<? extends Annotation> annotationType,
        @NonNull String annotationValue);

    /**
     * Returns the {@link BeanMethod} instances for this introspection.
     *
     * <p>The {@link BeanMethod} instances are only those methods annotated with {@code io.micronaut.context.annotation.Executable} and hence represent a subset
     * of the actual methods of the class and do not include any methods that are exposed as {@link BeanProperty} instances.</p>
     *
     * @return An immutable collection of methods.
     *
     * @since 2.3.0
     */
    @NonNull
    default Collection<BeanMethod<T, Object>> getBeanMethods() {
        return Collections.emptyList();
    }

    /**
     * Get first bean properties annotated for the given type.
     *
     * @param annotationType The annotation type
     * @return An immutable collection of properties.
     * @see io.micronaut.core.annotation.Introspected#indexed()
     */
    default @NonNull Optional<BeanProperty<T, Object>> getIndexedProperty(
        @NonNull Class<? extends Annotation> annotationType) {
        return getIndexedProperties(annotationType).stream().findFirst();
    }

    /**
     * The constructor arguments needed to instantiate the bean.
     *
     * @return An argument array
     */
    default @NonNull Argument<?>[] getConstructorArguments() {
        return Argument.ZERO_ARGUMENTS;
    }

    /**
     * Obtain a property by name.
     *
     * @param name The name of the property
     * @return A bean property if found
     */
    @NonNull
    default Optional<BeanProperty<T, Object>> getProperty(@NonNull String name) {
        return getBeanProperties().stream().filter(p -> p.getName().equals(name)).findFirst();
    }

    /**
     * Obtain a read property by name.
     *
     * @param name The name of the property
     * @return A bean read property if found
     * @since 4.4.0
     */
    @NonNull
    default Optional<BeanReadProperty<T, Object>> getReadProperty(@NonNull String name) {
        return getBeanReadProperties().stream().filter(p -> p.getName().equals(name)).findFirst();
    }

    /**
     * Obtain a write property by name.
     *
     * @param name The name of the property
     * @return A bean write property if found
     * @since 4.4.0
     */
    @NonNull
    default Optional<BeanWriteProperty<T, Object>> getWriteProperty(@NonNull String name) {
        return getBeanWriteProperties().stream().filter(p -> p.getName().equals(name)).findFirst();
    }

    /**
     * Obtain the property index position.
     *
     * @param name The name of the property
     * @return A property index or -1 of not found.
     * @since 3.1
     */
    default int propertyIndexOf(@NonNull String name) {
        int index = 0;
        for (BeanProperty<T, Object> property : getBeanProperties()) {
            if (property.getName().equals(name)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /**
     * Gets a property of the given name and type or throws {@link IntrospectionException} if the property is not present.
     *
     * @param name The name
     * @param type The type
     * @param <P>  The property generic type
     * @return The property
     */
    default @NonNull <P> BeanProperty<T, P> getRequiredProperty(@NonNull String name, @NonNull Class<P> type) {
        return getProperty(name, type)
            .orElseThrow(() -> new IntrospectionException("No property [" + name + "] of type [" + type + "] present"));
    }

    /**
     * Gets a read property of the given name and type or throws {@link IntrospectionException} if the property is not present.
     *
     * @param name The name
     * @param type The type
     * @param <P>  The property generic type
     * @return The property
     * @since 4.4.0
     */
    @NonNull
    default <P> BeanReadProperty<T, P> getRequiredReadProperty(@NonNull String name, @NonNull Class<P> type) {
        return getReadProperty(name, type)
            .orElseThrow(() -> new IntrospectionException("No read property [" + name + "] of type [" + type + "] present"));
    }

    /**
     * Gets a write property of the given name and type or throws {@link IntrospectionException} if the property is not present.
     *
     * @param name The name
     * @param type The type
     * @param <P>  The property generic type
     * @return The property
     * @since 4.4.0
     */
    @NonNull
    default <P> BeanWriteProperty<T, P> getRequiredWriteProperty(@NonNull String name, @NonNull Class<P> type) {
        return getWriteProperty(name, type)
            .orElseThrow(() -> new IntrospectionException("No write property [" + name + "] of type [" + type + "] present"));
    }

    /**
     * Obtain a property by name and type.
     *
     * @param name The name of the property
     * @param type The property type to search for
     * @param <P>  The property type
     * @return A bean property if found
     */
    default @NonNull <P> Optional<BeanProperty<T, P>> getProperty(@NonNull String name, @NonNull Class<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);

        final BeanProperty<T, ?> prop = getProperty(name).orElse(null);
        if (prop != null && type.isAssignableFrom(prop.getType())) {
            //noinspection unchecked
            return Optional.of((BeanProperty<T, P>) prop);
        }
        return Optional.empty();
    }

    /**
     * Obtain a read property by name and type.
     *
     * @param name The name of the property
     * @param type The property type to search for
     * @param <P>  The property type
     * @return A bean read property if found
     */
    default @NonNull <P> Optional<BeanReadProperty<T, P>> getReadProperty(@NonNull String name, @NonNull Class<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);

        final BeanReadProperty<T, ?> prop = getReadProperty(name).orElse(null);
        if (prop != null && type.isAssignableFrom(prop.getType())) {
            //noinspection unchecked
            return Optional.of((BeanReadProperty<T, P>) prop);
        }
        return Optional.empty();
    }

    /**
     * Obtain a write property by name and type.
     *
     * @param name The name of the property
     * @param type The property type to search for
     * @param <P>  The property type
     * @return A bean write property if found
     * @since 4.4.0
     */
    default @NonNull <P> Optional<BeanWriteProperty<T, P>> getWriteProperty(@NonNull String name, @NonNull Class<P> type) {
        ArgumentUtils.requireNonNull("name", name);
        ArgumentUtils.requireNonNull("type", type);

        final BeanWriteProperty<T, ?> prop = getWriteProperty(name).orElse(null);
        if (prop != null && type.isAssignableFrom(prop.getType())) {
            //noinspection unchecked
            return Optional.of((BeanProperty<T, P>) prop);
        }
        return Optional.empty();
    }

    /**
     * The property names as an array.
     *
     * @return The property names
     */
    default @NonNull String[] getPropertyNames() {
        return getBeanProperties().stream().map(BeanProperty::getName).toArray(String[]::new);
    }

    /**
     * The read property names as an array.
     *
     * @return The property names
     * @since 4.4.0
     */
    default @NonNull String[] getReadPropertyNames() {
        return getBeanReadProperties().stream().map(BeanReadProperty::getName).toArray(String[]::new);
    }

    /**
     * @return The bean constructor.
     * @since 3.0.0
     */
    default @NonNull BeanConstructor<T> getConstructor() {
        return new BeanConstructor<T>() {
            @Override
            public @NonNull Class<T> getDeclaringBeanType() {
                return getBeanType();
            }

            @Override
            public @NonNull Argument<?>[] getArguments() {
                return getConstructorArguments();
            }

            @Override
            public @NonNull T instantiate(Object... parameterValues) {
                return BeanIntrospection.this.instantiate(parameterValues);
            }
        };
    }

    /**
     * Obtains an introspection from the default {@link BeanIntrospector}.
     *
     * @param type The type
     * @param <T2> The generic type
     * @return The introspection
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException If the introspection cannot be found or errors when loading
     */
    static <T2> BeanIntrospection<T2> getIntrospection(Class<T2> type) {
        return BeanIntrospector.SHARED.getIntrospection(type);
    }

    /**
     * A builder style API for creating instances that abstracts whether the instance is a record, bean or created by another builder.
     *
     * @param <T> The bean type.
     * @since 4.1.0
     */
    @Experimental
    interface Builder<T> {
        /**
         * All the arguments possible for creating the instance.
         *
         * @return The arguments
         */
        @NonNull Argument<?>[] getBuilderArguments();

        /**
         * Any arguments required by the build method. If the build method has arguments these should be provided by {@link #build()}.
         *
         * @return The arguments
         */
        @NonNull Argument<?>[] getBuildMethodArguments();

        /**
         * @param name The argument name
         * @return The index.
         */
        int indexOf(String name);

        /**
         * Create the instance with the given value for the given property.
         *
         * @param name     The name of the argument
         * @param value    The value
         * @return This builder
         */
        @NonNull Builder<T> with(@NonNull String name, @Nullable Object value);

        /**
         * Create the instance copying properties from the given instance to start.
         *
         * @param existing The existing bean
         * @return This builder
         */
        @NonNull Builder<T> with(T existing);

        /**
         * Create the instance with the given value for the given property.
         *
         * @param index    The index of the argument
         * @param argument The argument
         * @param value    The value
         * @param <A>      The generic type
         * @return This builder
         */
        <A> @NonNull Builder<T> with(int index, @NonNull Argument<A> argument, @Nullable A value);

        /**
         * Create the instance with the given value for the given property.
         *
         * @param index             The index of the argument
         * @param argument          The argument
         * @param value             The value
         * @param conversionService The conversion service
         * @param <A>               The generic type
         * @return This builder
         */
        <A> @NonNull Builder<T> convert(int index, @NonNull ArgumentConversionContext<A> argument, @Nullable Object value, @NonNull ConversionService conversionService);

        /**
         * Build the instance from the current state.
         *
         * @return The built instance.
         * @throws IllegalArgumentException If one of the supplied inputs is invalid
         */
        @NonNull T build();

        /**
         * Build the instance from the current state and given parameters to the build method.
         *
         * @param params The params
         * @return The built instance.
         * @throws IllegalArgumentException If one of the supplied inputs is invalid
         */
        @NonNull T build(Object... params);
    }
}
