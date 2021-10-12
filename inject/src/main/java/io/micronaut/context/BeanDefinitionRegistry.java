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

import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>Core bean definition registry interface containing methods to find {@link BeanDefinition} instances.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinitionRegistry {

    /**
     * Return whether the bean of the given type is contained within this context.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier for the bean
     * @param <T>       The concrete type
     * @return True if it is
     */
    <T> boolean containsBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Return whether the bean of the given type is contained within this context.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier for the bean
     * @param <T>       The concrete type
     * @return True if it is
     * @since 3.0.0
     */
    default <T> boolean containsBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return containsBean(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Return whether the bean of the given type is contained within this context.
     *
     * @param beanType  The bean type
     * @param <T>       The concrete type
     * @return True if it is
     * @since 3.0.0
     */
    default <T> boolean containsBean(@NonNull Argument<T> beanType) {
        return containsBean(
                Objects.requireNonNull(beanType, "Bean type cannot be null"),
                null
        );
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      The bean type
     * @param singleton The singleton bean
     * @param qualifier The bean qualifier
     * @param inject    Whether the singleton should be injected (defaults to true)
     * @param <T>       The concrete type
     * @return This bean context
     */
    @NonNull <T> BeanDefinitionRegistry registerSingleton(
        @NonNull
        Class<T> type,
        @NonNull
        T singleton,
        @Nullable
        Qualifier<T> qualifier,
        boolean inject
    );

    /**
     * Obtain a bean configuration by name.
     *
     * @param configurationName The configuration name
     * @return An optional with the configuration either present or not
     */
    @NonNull Optional<BeanConfiguration> findBeanConfiguration(@NonNull String configurationName);

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Optional<BeanDefinition<T>> findBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType  The potentially parameterized type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @since 3.0.0
     */
    default @NonNull <T> Optional<BeanDefinition<T>> findBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBeanDefinition(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType  The potentially parameterized type
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @since 3.0.0
     */
    default @NonNull <T> Optional<BeanDefinition<T>> findBeanDefinition(@NonNull Argument<T> beanType) {
        return findBeanDefinition(beanType, null);
    }

    /**
     * Obtain a {@link BeanDefinition} for the given bean.
     *
     * @param bean The bean
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Optional<BeanRegistration<T>> findBeanRegistration(@NonNull T bean);


    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param <T>      The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@NonNull Class<T> beanType);

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param <T>      The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible
     * bean definitions exist for the given type
     * @since 3.0.0
     */
    default @NonNull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@NonNull Argument<T> beanType) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanDefinitions(beanType.getType(), null);
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible
     * bean definitions exist for the given type
     * @since 3.0.0
     */
    default @NonNull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanDefinitions(beanType.getType(), qualifier);
    }


    /**
     * Get all of the {@link BeanDefinition} for the given qualifier.
     *
     * @param qualifier The qualifier
     * @return The bean definitions
     */
    @NonNull Collection<BeanDefinition<?>> getBeanDefinitions(@NonNull Qualifier<Object> qualifier);

    /**
     * Get all of the registered {@link BeanDefinition}.
     *
     * @return The bean definitions
     */
    @NonNull Collection<BeanDefinition<?>> getAllBeanDefinitions();

    /**
     * Get all of the enabled {@link BeanDefinitionReference}.
     *
     * @return The bean definitions
     */
    @NonNull Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences();

    /**
     * Find active {@link javax.inject.Singleton} beans for the given qualifier. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param qualifier The qualifier
     * @return The beans
     */
    @NonNull Collection<BeanRegistration<?>> getActiveBeanRegistrations(@NonNull Qualifier<?> qualifier);

    /**
     * Find active {@link javax.inject.Singleton} beans for the given bean type. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param <T>      The concrete type
     * @return The beans
     */
    @NonNull <T> Collection<BeanRegistration<T>> getActiveBeanRegistrations(@NonNull Class<T> beanType);

    /**
     * Find and if necessary initialize {@link javax.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param <T>      The concrete type
     * @return The beans
     */
    @NonNull <T> Collection<BeanRegistration<T>> getBeanRegistrations(@NonNull Class<T> beanType);

    /**
     * Find and if necessary initialize {@link javax.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return The beans
     * @since 2.4.0
     */
    @NonNull <T> Collection<BeanRegistration<T>> getBeanRegistrations(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Find and if necessary initialize {@link javax.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return The beans
     * @since 3.0.0
     */
    default @NonNull <T> Collection<BeanRegistration<T>> getBeanRegistrations(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistrations(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Find a bean registration for the given bean type and optional qualifier.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return The bean registration
     * @throws NoSuchBeanException if the bean doesn't exist
     * @since 2.4.0
     */
    @NonNull <T> BeanRegistration<T> getBeanRegistration(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Find a bean registration for the given bean type and optional qualifier.
     *
     * @param beanType The potentially parameterized bean type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return The bean registration
     * @throws NoSuchBeanException if the bean doesn't exist
     * @since 3.0.0
     */
    default @NonNull <T> BeanRegistration<T> getBeanRegistration(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default @NonNull <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return findProxyTargetBeanDefinition(
                beanType.getType(),
                qualifier
        );
    }

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @NonNull <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      The bean type
     * @param singleton The singleton bean
     * @param qualifier The bean qualifier
     * @param <T>       The concrete type
     * @return This bean context
     */
    default @NonNull <T> BeanDefinitionRegistry registerSingleton(
        @NonNull Class<T> type,
        @NonNull T singleton,
        @Nullable Qualifier<T> qualifier
    ) {
        return registerSingleton(type, singleton, qualifier, true);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      the bean type
     * @param singleton The singleton bean
     * @param <T>       The concrete type
     * @return This bean context
     */
    default <T> BeanDefinitionRegistry registerSingleton(
        @NonNull Class<T> type,
        @NonNull T singleton
    ) {
        return registerSingleton(type, singleton, null);
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     */
    default @NonNull <T> BeanDefinition<T> getBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBeanDefinition(beanType, qualifier).orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType  The potentially parameterized type type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     * @since 3.0.
     */
    default @NonNull <T> BeanDefinition<T> getBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBeanDefinition(beanType, qualifier).orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     */
    default @NonNull <T> BeanDefinition<T> getProxyTargetBeanDefinition(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findProxyTargetBeanDefinition(beanType, qualifier).orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     * @since 3.0.0
     */
    default @NonNull <T> BeanDefinition<T> getProxyTargetBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findProxyTargetBeanDefinition(beanType, qualifier).orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param <T>      The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     */
    default @NonNull <T> BeanDefinition<T> getBeanDefinition(@NonNull Class<T> beanType) {
        return findBeanDefinition(beanType, null).orElseThrow(() -> new NoSuchBeanException(beanType));
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param <T>      The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     * @since 3.0.0
     */
    default @NonNull <T> BeanDefinition<T> getBeanDefinition(@NonNull Argument<T> beanType) {
        return getBeanDefinition(beanType, null);
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param <T>      The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default @NonNull <T> Optional<BeanDefinition<T>> findBeanDefinition(@NonNull Class<T> beanType) {
        return findBeanDefinition(beanType, null);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     * @return This bean context
     */
    default @NonNull BeanDefinitionRegistry registerSingleton(@NonNull Object singleton) {
        ArgumentUtils.requireNonNull("singleton", singleton);
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     * @param inject    Whether the singleton should be injected (defaults to true)
     * @return This bean context
     */
    default @NonNull BeanDefinitionRegistry registerSingleton(@NonNull Object singleton, boolean inject) {
        ArgumentUtils.requireNonNull("singleton", singleton);
        Class type = singleton.getClass();
        return registerSingleton(
            type,
            singleton,
            null,
            inject
        );
    }

    /**
     * Return whether the bean of the given type is contained within this context.
     *
     * @param beanType The bean type
     * @return True if it is
     */
    @SuppressWarnings("ConstantConditions")
    default boolean containsBean(@NonNull Class<?> beanType) {
        return beanType != null && containsBean(beanType, null);
    }
}
