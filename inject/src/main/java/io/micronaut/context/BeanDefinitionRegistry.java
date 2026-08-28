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
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ProxyBeanDefinition;

import java.util.Collection;
import java.util.Collections;
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
    <T> boolean containsBean(Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Return whether the bean of the given type is contained within this context.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier for the bean
     * @param <T>       The concrete type
     * @return True if it is
     * @since 3.0.0
     */
    default <T> boolean containsBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default <T> boolean containsBean(Argument<T> beanType) {
        return containsBean(
                Objects.requireNonNull(beanType, "Bean type cannot be null"),
                null
        );
    }

    /**
     * Registers a bean configuration. This allows disabling a set of beans based on a condition.
     *
     * @param configuration The configuration
     * @return The registry
     * @since 4.8.0
     */
    @Experimental
    default BeanDefinitionRegistry registerBeanConfiguration(BeanConfiguration configuration) {
        throw new UnsupportedOperationException("This implementation of BeanDefinitionRegistry doesn't support runtime registration of bean configurations");
    }

    /**
     * Registers a new reference at runtime. Not that registering beans can impact
     * the object graph therefore should this should be done as soon as possible prior to
     * the creation of other beans preferably with a high priority {@link io.micronaut.context.annotation.Context} scope bean.
     *
     * @param definition The reference.
     * @return The registry
     * @param <B> The bean type
     * @since 3.6.0
     */
    @Experimental
    default <B> BeanDefinitionRegistry registerBeanDefinition(RuntimeBeanDefinition<B> definition) {
        throw new UnsupportedOperationException("This implementation of BeanDefinitionRegistry doesn't support runtime registration of bean definitions");
    }


    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      The bean type
     * @param singleton The singleton bean
     * @param qualifier The bean qualifier
     * @param inject    Whether the singleton should be injected (defaults to true)
     * @param <T>       The concrete type
     * @return This bean context
     */
    <T> BeanDefinitionRegistry registerSingleton(
        Class<T> type,
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
    Optional<BeanConfiguration> findBeanConfiguration(String configurationName);

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
    <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier);

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
    default <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default <T> Optional<BeanDefinition<T>> findBeanDefinition(Argument<T> beanType) {
        return findBeanDefinition(beanType, null);
    }

    /**
     * Obtain a {@link BeanRegistration} for the given bean.
     *
     * @param bean The bean
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean);

    /**
     * Obtain a {@link BeanDefinition} for the given bean.
     *
     * @param bean The bean
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @since 4.3.0
     */
    default <T> Optional<BeanDefinition<T>> findBeanDefinition(T bean) {
        return findBeanRegistration(bean).map(BeanRegistration::getBeanDefinition);
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
    <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType);

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
    default <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> beanType) {
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
    <T> Collection<BeanDefinition<T>> getBeanDefinitions(Class<T> beanType, @Nullable Qualifier<T> qualifier);

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
    default <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return getBeanDefinitions(beanType.getType(), qualifier);
    }


    /**
     * Get all of the {@link BeanDefinition} for the given qualifier.
     *
     * @param qualifier The qualifier
     * @return The bean definitions
     */
    Collection<BeanDefinition<Object>> getBeanDefinitions(Qualifier<Object> qualifier);

    /**
     * Get all registered {@link BeanDefinition}.
     *
     * @return The bean definitions
     */
    Collection<BeanDefinition<Object>> getAllBeanDefinitions();

    /**
     * Get all enabled {@link BeanDefinitionReference}.
     *
     * @return The bean definitions
     */
    Collection<BeanDefinitionReference<Object>> getBeanDefinitionReferences();

    /**
     * Get all disabled {@link DisabledBean}.
     *
     * @return The disabled bean definitions
     * @since 4.0.0
     */
    default Collection<DisabledBean<?>> getDisabledBeans() {
        return Collections.emptyList();
    }

    /**
     * Find active {@link jakarta.inject.Singleton} beans for the given qualifier. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param qualifier The qualifier
     * @return The beans
     */
    Collection<BeanRegistration<?>> getActiveBeanRegistrations(Qualifier<?> qualifier);

    /**
     * Find active {@link jakarta.inject.Singleton} beans for the given bean type. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param <T>      The concrete type
     * @return The beans
     */
    <T> Collection<BeanRegistration<T>> getActiveBeanRegistrations(Class<T> beanType);

    /**
     * Find and if necessary initialize {@link jakarta.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param <T>      The concrete type
     * @return The beans
     */
    <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType);

    /**
     * Find and if necessary initialize {@link jakarta.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return The beans
     * @since 2.4.0
     */
    <T> Collection<BeanRegistration<T>> getBeanRegistrations(Class<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Find and if necessary initialize {@link jakarta.inject.Singleton} beans for the given bean type, returning all the active registrations. Note that
     * this method can return multiple registrations for a given singleton bean instance since each bean may have multiple qualifiers.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param <T>      The concrete type
     * @return The beans
     * @since 3.0.0
     */
    default <T> Collection<BeanRegistration<T>> getBeanRegistrations(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    <T> BeanRegistration<T> getBeanRegistration(Class<T> beanType, @Nullable Qualifier<T> qualifier);

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
    default <T> BeanRegistration<T> getBeanRegistration(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(
                Objects.requireNonNull(beanType, "Bean type cannot be null").getType(),
                qualifier
        );
    }

    /**
     * Find a bean registration for the given bean definition.
     *
     * @param beanDefinition The bean definition
     * @param <T>            The concrete type
     * @return The bean registration
     * @throws NoSuchBeanException if the bean doesn't exist
     * @since 3.5.0
     */
    <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<T> beanDefinition);

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
    <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier);

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
    default <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        Objects.requireNonNull(beanType, "Bean type cannot be null");
        return findProxyTargetBeanDefinition(
                beanType.getType(),
                qualifier
        );
    }

    /**
     * Obtain the original {@link BeanDefinition} for a {@link io.micronaut.inject.ProxyBeanDefinition}.
     *
     * @param proxyBeanDefinition  The proxy bean definition
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist for the given type
     * @since 3.5.0
     */
    default <T> Optional<BeanDefinition<T>> findProxyTargetBeanDefinition(BeanDefinition<T> proxyBeanDefinition) {
        Objects.requireNonNull(proxyBeanDefinition, "Proxy bean definition cannot be null");
        if (proxyBeanDefinition instanceof ProxyBeanDefinition<T> beanDefinition) {
            return findProxyTargetBeanDefinition(
                beanDefinition.getTargetType(),
                proxyBeanDefinition.getDeclaredQualifier()
            );
        }
        return Optional.empty();
    }

    /**
     * Obtain the proxy {@link BeanDefinition} for the bean of type and qualifier.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findProxyBeanDefinition(Argument.of(beanType), qualifier);
    }

    /**
     * Obtain the proxy {@link BeanDefinition} for the bean of type and qualifier.
     *
     * @param beanType  The type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    <T> Optional<BeanDefinition<T>> findProxyBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      The bean type
     * @param singleton The singleton bean
     * @param qualifier The bean qualifier
     * @param <T>       The concrete type
     * @return This bean context
     * @deprecated Use {@link #registerBeanDefinition(RuntimeBeanDefinition)}
     */
    @Deprecated(forRemoval = true, since = "5.0")
    default <T> BeanDefinitionRegistry registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier) {
        return registerSingleton(type, singleton, qualifier, true);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param type      the bean type
     * @param singleton The singleton bean
     * @param <T>       The concrete type
     * @return This bean context
     * @deprecated Use {@link #registerBeanDefinition(RuntimeBeanDefinition)}
     */
    @Deprecated(forRemoval = true, since = "5.0")
    default <T> BeanDefinitionRegistry registerSingleton(Class<T> type, T singleton) {
        return registerSingleton(type, singleton, null);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     * @return This bean context
     * @deprecated Use {@link #registerBeanDefinition(RuntimeBeanDefinition)}
     */
    @Deprecated(forRemoval = true, since = "5.0")
    default BeanDefinitionRegistry registerSingleton(Object singleton) {
        ArgumentUtils.requireNonNull("singleton", singleton);
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been
     * compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by
     * invoking any {@link jakarta.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     * @param inject    Whether the singleton should be injected (defaults to true)
     * @return This bean context
     * @deprecated Use {@link #registerBeanDefinition(RuntimeBeanDefinition)}
     */
    @Deprecated(forRemoval = true, since = "5.0")
    default BeanDefinitionRegistry registerSingleton(Object singleton, boolean inject) {
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
    default <T> BeanDefinition<T> getBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return findBeanDefinition(beanType, qualifier).orElseThrow(() -> new NoSuchBeanException(beanType, qualifier));
    }

    /**
     * Obtain a {@link BeanDefinition} for the given type.
     *
     * @param beanType  The potentially parameterized type
     * @param qualifier The qualifier
     * @param <T>       The concrete type
     * @return The {@link BeanDefinition}
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @throws NoSuchBeanException                                    If the bean cannot be found
     * @since 3.0.
     */
    default <T> BeanDefinition<T> getBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default <T> BeanDefinition<T> getProxyTargetBeanDefinition(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default <T> BeanDefinition<T> getProxyTargetBeanDefinition(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
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
    default <T> BeanDefinition<T> getBeanDefinition(Class<T> beanType) {
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
    default <T> BeanDefinition<T> getBeanDefinition(Argument<T> beanType) {
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
    default <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType) {
        return findBeanDefinition(beanType, null);
    }

    /**
     * Return whether the bean of the given type is contained within this context.
     *
     * @param beanType The bean type
     * @return True if it is
     */
    @SuppressWarnings("ConstantConditions")
    default boolean containsBean(Class<?> beanType) {
        return beanType != null && containsBean(beanType, null);
    }
}
