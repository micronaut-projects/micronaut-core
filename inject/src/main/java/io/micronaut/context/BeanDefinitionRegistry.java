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
package io.micronaut.context;

import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
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
    <T> boolean containsBean(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link javax.annotation.PostConstruct} hooks.</p>
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
    @Nonnull <T> BeanDefinitionRegistry registerSingleton(
        @Nonnull
        Class<T> type,
        @Nonnull
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
    @Nonnull Optional<BeanConfiguration> findBeanConfiguration(@Nonnull String configurationName);

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
    @Nonnull <T> Optional<BeanDefinition<T>> findBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier);


    /**
     * Obtain a {@link BeanDefinition} for the given bean.
     *
     * @param bean The bean
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @Nonnull <T> Optional<BeanRegistration<T>> findBeanRegistration(@Nonnull T bean);

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType The type
     * @param <T>      The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    @Nonnull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@Nonnull Class<T> beanType);

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
    @Nonnull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier);


    /**
     * Get all of the {@link BeanDefinition} for the given qualifier.
     *
     * @param qualifier The qualifer
     * @return The bean definitions
     */
    @Nonnull Collection<BeanDefinition<?>> getBeanDefinitions(@Nonnull Qualifier<Object> qualifier);

    /**
     * Get all of the registered {@link BeanDefinition}.
     *
     * @return The bean definitions
     */
    @Nonnull Collection<BeanDefinition<?>> getAllBeanDefinitions();

    /**
     * Get all of the enabled {@link BeanDefinitionReference}.
     *
     * @return The bean definitions
     */
    @Nonnull Collection<BeanDefinitionReference<?>> getBeanDefinitionReferences();

    /**
     * Find active {@link javax.inject.Singleton} beans for the given qualifier. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param qualifier The qualifier
     * @return The beans
     */
    @Nonnull Collection<BeanRegistration<?>> getActiveBeanRegistrations(@Nonnull Qualifier<?> qualifier);

    /**
     * Find active {@link javax.inject.Singleton} beans for the given bean type. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param <T>      The concrete type
     * @return The beans
     */
    @Nonnull <T> Collection<BeanRegistration<T>> getActiveBeanRegistrations(@Nonnull Class<T> beanType);

    /**
     * Find and if necessary initialize {@link javax.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param <T>      The concrete type
     * @return The beans
     */
    @Nonnull <T> Collection<BeanRegistration<T>> getBeanRegistrations(@Nonnull Class<T> beanType);

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
    @Nonnull <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier);

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
    @Nonnull <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link javax.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      The bean type
     * @param singleton The singleton bean
     * @param qualifier The bean qualifier
     * @param <T>       The concrete type
     * @return This bean context
     */
    default @Nonnull <T> BeanDefinitionRegistry registerSingleton(
        @Nonnull Class<T> type,
        @Nonnull T singleton,
        @Nullable Qualifier<T> qualifier
    ) {
        return registerSingleton(type, singleton, qualifier, true);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link javax.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      the bean type
     * @param singleton The singleton bean
     * @param <T>       The concrete type
     * @return This bean context
     */
    default <T> BeanDefinitionRegistry registerSingleton(
        @Nonnull Class<T> type,
        @Nonnull T singleton
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
    default @Nonnull <T> BeanDefinition<T> getBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default @Nonnull <T> BeanDefinition<T> getProxyTargetBeanDefinition(@Nonnull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default @Nonnull <T> BeanDefinition<T> getBeanDefinition(@Nonnull Class<T> beanType) {
        return findBeanDefinition(beanType, null).orElseThrow(() -> new NoSuchBeanException(beanType));
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
    default @Nonnull <T> Optional<BeanDefinition<T>> findBeanDefinition(@Nonnull Class<T> beanType) {
        return findBeanDefinition(beanType, null);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link javax.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     * @return This bean context
     */
    default @Nonnull BeanDefinitionRegistry registerSingleton(@Nonnull Object singleton) {
        ArgumentUtils.requireNonNull("singleton", singleton);
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     * <p>
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link javax.annotation.PostConstruct} hooks.</p>
     * <p>
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     * @param inject    Whether the singleton should be injected (defaults to true)
     * @return This bean context
     */
    default @Nonnull BeanDefinitionRegistry registerSingleton(@Nonnull Object singleton, boolean inject) {
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
    default boolean containsBean(@Nonnull Class beanType) {
        return beanType != null && containsBean(beanType, null);
    }
}
