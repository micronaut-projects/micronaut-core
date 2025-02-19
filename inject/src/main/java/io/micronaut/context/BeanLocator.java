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
package io.micronaut.context;

import io.micronaut.core.reflect.InstantiationUtils;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericArgument;
import io.micronaut.inject.BeanDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>Core interface for locating and discovering {@link io.micronaut.context.annotation.Bean} instances.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanLocator {

    /**
     * Obtains a Bean for the given bean definition.
     *
     * @param definition  The bean type
     * @param <T>       The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    @NonNull <T> T getBean(@NonNull BeanDefinition<T> definition);

    /**
     * Obtains a Bean for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    @NonNull <T> T getBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtains a Bean for the given type and qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 3.0.0
     */
    default @NonNull <T> T getBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBean(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Obtains a Bean for the given type and qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param <T>       The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 3.0.0
     */
    default @NonNull <T> T getBean(@NonNull Argument<T> beanType) {
        return getBean(
                beanType,
                null
        );
    }

    /**
     * Obtains a {@link BeanProvider} for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return The provider
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.5.0
     */
    default @NonNull <T> BeanProvider<T> getProvider(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getProvider(Argument.of(beanType), qualifier);
    }

    /**
     * Obtains a {@link BeanProvider} for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param <T>       The bean type parameter
     * @return The provider
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.5.0
     */
    default @NonNull <T> BeanProvider<T> getProvider(@NonNull Class<T> beanType) {
        return getProvider(Argument.of(beanType), null);
    }

    /**
     * Obtains a {@link BeanProvider} for the given type and qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return The provider
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.5.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default @NonNull <T> BeanProvider<T> getProvider(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Argument providerArgument = Argument.of(BeanProvider.class, beanType);
        return (BeanProvider<T>) getBean(
            providerArgument,
            qualifier
        );
    }

    /**
     * Obtains a {@link BeanProvider} for the given type and qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param <T>       The bean type parameter
     * @return A provider
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.5.0
     */
    default @NonNull <T> BeanProvider<T> getProvider(@NonNull Argument<T> beanType) {
        return getProvider(
            beanType,
            null
        );
    }

    /**
     * Finds a Bean for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 3.0.0
     */
    @NonNull <T> Optional<T> findBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Finds a Bean for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param <T>       The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 3.0.0
     */
    default @NonNull <T> Optional<T> findBean(@NonNull Argument<T> beanType) {
        return findBean(beanType, null);
    }

    /**
     * Finds a Bean for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    @NonNull <T> Optional<T> findBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Get all beans of the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return The found beans
     */
    @NonNull <T> Collection<T> getBeansOfType(@NonNull Class<T> beanType);

    /**
     * Get all beans of the given type.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return The found beans
     */
    @NonNull <T> Collection<T> getBeansOfType(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Get all beans of the given type.
     *
     * @param beanType The potenitally parameterized bean type
     * @param <T>      The bean type parameter
     * @return The found beans
     * @since 3.0.0
     */
    default @NonNull <T> Collection<T> getBeansOfType(@NonNull Argument<T> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeansOfType(beanType.getType());
    }

    /**
     * Get all beans of the given type.
     *
     * @param beanType  The potenitally parameterized bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return The found beans
     * @since 3.0.0
     */
    default @NonNull <T> Collection<T> getBeansOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeansOfType(beanType.getType(), qualifier);
    }

    /**
     * Obtain a stream of beans of the given type.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean concrete type
     * @return A stream of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    @NonNull <T> Stream<T> streamOfType(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtain a stream of beans of the given type.
     *
     * @param beanType  The potentially parameterized bean type
     * @param qualifier The qualifier
     * @param <T>       The bean concrete type
     * @return A stream of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 3.0.0
     */
    default @NonNull <T> Stream<T> streamOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return streamOfType(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Obtain a stream of beans of the given type.
     *
     * @param beanType  The potentially parameterized bean type
     * @param <T>       The bean concrete type
     * @return A stream of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 3.0.0
     */
    default @NonNull <T> Stream<T> streamOfType(@NonNull Argument<T> beanType) {
        return streamOfType(
                Objects.requireNonNull(beanType, "Bean type cannot be null"),
                null
        );
    }

    /**
     * Obtain a map of beans of the given type where the key is the qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param qualifier The qualifier
     * @param <V>       The bean concrete type
     * @return A map of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.0.0
     */
    default @NonNull <V> Map<String, V> mapOfType(@NonNull Argument<V> beanType, @Nullable Qualifier<V> qualifier) {
        return Collections.emptyMap();
    }

    /**
     * Obtain a map of beans of the given type where the key is the qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param qualifier The qualifier
     * @param <V>       The bean concrete type
     * @return A map of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.0.0
     */
    default @NonNull <V> Map<String, V> mapOfType(@NonNull Class<V> beanType, @Nullable Qualifier<V> qualifier) {
        return mapOfType(Argument.of(beanType), qualifier);
    }

    /**
     * Obtain a map of beans of the given type where the key is the qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param <V>       The bean concrete type
     * @return A map of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.0.0
     */
    default @NonNull <V> Map<String, V> mapOfType(@NonNull Class<V> beanType) {
        return mapOfType(Argument.of(beanType), null);
    }

    /**
     * Obtain a map of beans of the given type where the key is the qualifier.
     *
     * @param beanType  The potentially parameterized bean type
     * @param <V>       The bean concrete type
     * @return A map of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     * @since 4.0.0
     */
    default @NonNull <V> Map<String, V> mapOfType(@NonNull Argument<V> beanType) {
        return mapOfType(beanType, null);
    }

    /**
     * Resolves the proxy target for a given bean type. If the bean has no proxy then the original bean is returned.
     *
     * @param beanType  The bean type
     * @param qualifier The bean qualifier
     * @param <T>       The generic type
     * @return The proxied instance
     */
    @NonNull <T> T getProxyTargetBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Resolves the proxy target for a given bean type. If the bean has no proxy then the original bean is returned.
     *
     * @param beanType  The bean type
     * @param qualifier The bean qualifier
     * @param <T>       The generic type
     * @return The proxied instance
     * @since 3.0.0
     */
    default @NonNull <T> T getProxyTargetBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getProxyTargetBean(Objects.requireNonNull(beanType, "Bean type cannot be null").getType(), qualifier);
    }

    /**
     * Obtain a stream of beans of the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean concrete type
     * @return A stream
     */
    default @NonNull <T> Stream<T> streamOfType(@NonNull Class<T> beanType) {
        return streamOfType(beanType, null);
    }

    /**
     * Obtains a Bean for the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws io.micronaut.context.exceptions.NoSuchBeanException If the bean doesn't exist
     */
    default @NonNull <T> T getBean(@NonNull Class<T> beanType) {
        return getBean(beanType, null);
    }

    /**
     * Finds a Bean for the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default @NonNull <T> Optional<T> findBean(@NonNull Class<T> beanType) {
        return findBean(beanType, null);
    }

    /**
     * Finds a Bean for the given type or attempts to instantiate the given instance.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean if it could not
     * be found or instantiated
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default @NonNull <T> Optional<T> findOrInstantiateBean(@NonNull Class<T> beanType) {
        Optional<T> bean = findBean(beanType, null);
        if (bean.isPresent()) {
            return bean;
        } else {
            return InstantiationUtils.tryInstantiate(beanType);
        }
    }
}
