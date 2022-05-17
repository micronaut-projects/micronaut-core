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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The singleton scope implementation.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class SingletonScope {

    /**
     * The locks used to prevent re-creating of the same singleton.
     */
    private final Map<BeanDefinitionIdentity, Object> singletonsInCreationLocks = new ConcurrentHashMap<>(5, 1);

    /**
     * The main collection storing registrations for {@link BeanDefinition}.
     */
    private final Map<BeanDefinitionIdentity, BeanRegistration> singletonByBeanDefinition = new ConcurrentHashMap<>(100);

    /**
     * Index collection to retrieve a registration by {@link Argument} and {@link Qualifier}.
     */
    private final Map<DefaultBeanContext.BeanKey, BeanRegistration> singletonByArgumentAndQualifier = new ConcurrentHashMap<>(100);

    @NonNull
    <T> BeanRegistration<T> getOrCreate(@NonNull DefaultBeanContext beanContext,
                                        @Nullable BeanResolutionContext resolutionContext,
                                        @NonNull BeanDefinition<T> definition,
                                        @NonNull Argument<T> beanType,
                                        @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> beanRegistration = findBeanRegistration(definition, beanType, qualifier);
        if (beanRegistration != null) {
            return beanRegistration;
        }
        BeanDefinitionIdentity identity = BeanDefinitionIdentity.of(definition);
        BeanRegistration<T> existingRegistration = singletonByBeanDefinition.get(identity);
        if (existingRegistration != null) {
            return existingRegistration;
        }
        Object lock = singletonsInCreationLocks.computeIfAbsent(identity, beanDefinitionIdentity -> new Object());
        synchronized (lock) {
            try {
                existingRegistration = singletonByBeanDefinition.get(identity);
                if (existingRegistration != null) {
                    return existingRegistration;
                }
                BeanRegistration<T> newRegistration = beanContext.createRegistration(resolutionContext, beanType, qualifier, definition, false);
                registerSingletonBean(newRegistration, qualifier);
                return newRegistration;
            } finally {
                singletonsInCreationLocks.remove(identity);
            }
        }
    }

    /**
     * Register singleton.
     *
     * @param registration The bean registration
     * @param qualifier    The qualifier
     * @param <T>          The singleton type
     * @return The new registration
     */
    @NonNull
    <T> BeanRegistration<T> registerSingletonBean(@NonNull BeanRegistration<T> registration, Qualifier qualifier) {

        BeanDefinition<T> beanDefinition = registration.beanDefinition;
        singletonByBeanDefinition.put(BeanDefinitionIdentity.of(beanDefinition), registration);
        if (!beanDefinition.isSingleton()) {
            // In some cases you can register an instance of non-singleton bean and expect it act as a singleton
            // Current test `RegisterSingletonSpec "test register singleton method"` does it because of the present @Inject annotation
            // This might be something to remove in 4.0
            DefaultBeanContext.BeanKey<T> beanKey = new DefaultBeanContext.BeanKey<>(beanDefinition, qualifier);
            singletonByArgumentAndQualifier.put(beanKey, registration);
        }
        if (beanDefinition instanceof BeanDefinitionDelegate || beanDefinition instanceof NoInjectionBeanDefinition) {
            // Special cases when custom bean definitions need to be indexed:
            // BeanDefinitionDelegate - doesn't really exist with a custom qualifier
            // NoInjectionBeanDefinition - cannot be properly selected from 'beanDefinitionsClasses' when is used with a custom qualifier
            DefaultBeanContext.BeanKey<T> beanKey = new DefaultBeanContext.BeanKey<>(beanDefinition, beanDefinition.getDeclaredQualifier());
            singletonByArgumentAndQualifier.put(beanKey, registration);
        }
        if (registration.bean != null && registration.bean.getClass() != beanDefinition.getBeanType()) {
            // If the actual type differs, allow to inject the actual implementation for cases like:
            // `MyInterface factoryBean() { new Impl.. }`
            // This might be something to remove in 4.0
            DefaultBeanContext.BeanKey<T> concrete = new DefaultBeanContext.BeanKey<>((Class<T>) registration.bean.getClass(), qualifier);
            singletonByArgumentAndQualifier.put(concrete, registration);
        }
        return registration;
    }

    /**
     * Fast check if singleton is present.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The singleton type
     * @return true if contains, false doesn't mean the singleton is not present.
     */
    <T> boolean containsBean(Argument<T> beanType, Qualifier<T> qualifier) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        DefaultBeanContext.BeanKey<T> beanKey = new DefaultBeanContext.BeanKey<>(beanType, qualifier);
        return singletonByArgumentAndQualifier.containsKey(beanKey);
    }

    /**
     * @return Active singleton registrations
     */
    @NonNull
    Collection<BeanRegistration> getBeanRegistrations() {
        return singletonByBeanDefinition.values();
    }

    /**
     * @param qualifier The qualifier
     * @return Active singleton registrations by qualifier
     */
    @NonNull
    Collection<BeanRegistration<?>> getBeanRegistrations(@NonNull Qualifier<?> qualifier) {
        List<BeanRegistration<?>> beanRegistrations = new ArrayList<>();
        for (BeanRegistration<?> beanRegistration : singletonByBeanDefinition.values()) {
            BeanDefinition beanDefinition = beanRegistration.beanDefinition;
            if (qualifier.reduce(beanDefinition.getBeanType(), Stream.of(beanDefinition)).findFirst().isPresent()) {
                beanRegistrations.add(beanRegistration);
            }
        }
        return beanRegistrations;
    }

    /**
     * @param beanType The beanType
     * @param <T>      The bean type
     * @return Active singleton registrations by beanType
     */
    @SuppressWarnings("unchecked")
    @NonNull
    <T> Collection<BeanRegistration<T>> getBeanRegistrations(@NonNull Class<T> beanType) {
        List<BeanRegistration<T>> beanRegistrations = new ArrayList<>();
        for (BeanRegistration<?> beanRegistration : singletonByBeanDefinition.values()) {
            BeanDefinition beanDefinition = beanRegistration.beanDefinition;
            if (beanType.isAssignableFrom(beanDefinition.getBeanType())) {
                beanRegistrations.add((BeanRegistration<T>) beanRegistration);
            }
        }
        return beanRegistrations;
    }

    /**
     * Find active bean registration by provided bean definition and qualifier.
     *
     * @param beanDefinition The beanDefinition
     * @param qualifier      The qualifier
     * @param <T>            The bean type
     * @return found registration or null
     */
    @Nullable
    <T> BeanRegistration<T> findBeanRegistration(@NonNull BeanDefinition<T> beanDefinition, @Nullable Qualifier<T> qualifier) {
        return findBeanRegistration(beanDefinition, beanDefinition.asArgument(), qualifier);
    }

    /**
     * Find active bean registration by provided identifier.
     *
     * @param identifier The identifier
     * @param <T>        The bean type
     * @return found registration or null
     */
    @Nullable
    <T> BeanRegistration<T> findBeanRegistration(@NonNull BeanIdentifier identifier) {
        for (BeanRegistration registration : singletonByBeanDefinition.values()) {
            if (registration.identifier.equals(identifier)) {
                return registration;
            }
        }
        return null;
    }

    /**
     * Find active bean registration by provided bean instance.
     *
     * @param bean The bean
     * @param <T>  The bean type
     * @return found registration or null
     */
    @Nullable
    <T> BeanRegistration<T> findBeanRegistration(@Nullable T bean) {
        if (bean == null) {
            return null;
        }
        for (BeanRegistration beanRegistration : singletonByBeanDefinition.values()) {
            if (bean == beanRegistration.getBean()) {
                return beanRegistration;
            }
        }
        return null;
    }

    /**
     * Find active bean registration by provided bean definition.
     *
     * @param definition The
     * @param <T>        The bean type
     * @return found registration or null
     */
    @Nullable
    <T> BeanRegistration<T> findBeanRegistration(@NonNull BeanDefinition<T> definition) {
        return singletonByBeanDefinition.get(BeanDefinitionIdentity.of(definition));
    }

    /**
     * Find active bean registration by provided bean definition, beanType and qualifier.
     *
     * @param beanDefinition The beanDefinition
     * @param beanType       The beanType
     * @param qualifier      The qualifier
     * @param <T>            The bean type
     * @return found registration or null
     */
    @Nullable
    <T> BeanRegistration<T> findBeanRegistration(@NonNull BeanDefinition<T> beanDefinition,
                                                 @NonNull Argument<T> beanType,
                                                 @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> beanRegistration = singletonByBeanDefinition.get(BeanDefinitionIdentity.of(beanDefinition));
        if (beanRegistration == null) {
            return findCachedSingletonBeanRegistration(beanType, qualifier);
        }
        return beanRegistration;
    }

    /**
     * Find cached singleton registration by beanType and qualifier.
     * <p>
     * If the result is null it doesn't mean singleton is not present.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The type
     * @return found registration.
     */
    @Nullable
    <T> BeanRegistration<T> findCachedSingletonBeanRegistration(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        DefaultBeanContext.BeanKey<T> beanKey = new DefaultBeanContext.BeanKey<>(beanType, qualifier);
        BeanRegistration<T> beanRegistration = singletonByArgumentAndQualifier.get(beanKey);
        if (beanRegistration != null && beanRegistration.bean != null) {
            return beanRegistration;
        }
        return null;
    }

    /**
     * Find cached singleton registration's bean definition by beanType and qualifier.
     * <p>
     * If the result is null it doesn't mean singleton is not present.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The type
     * @return found registration's bean definition.
     */
    @Nullable
    <T> BeanDefinition<T> findCachedSingletonBeanDefinition(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        BeanRegistration<T> reg = findCachedSingletonBeanRegistration(beanType, qualifier);
        if (reg != null) {
            return reg.getBeanDefinition();
        }
        return null;
    }

    /**
     * Purge cached instances by {@link BeanDefinition} and an instance.
     *
     * @param beanDefinition The bean definition
     * @param bean           The bean
     * @param <T>            The bean type
     */
    synchronized <T> void purgeCacheForBeanInstance(BeanDefinition<T> beanDefinition, T bean) {
        singletonByBeanDefinition.remove(BeanDefinitionIdentity.of(beanDefinition));
        singletonByArgumentAndQualifier.entrySet().removeIf(entry -> entry.getKey().beanType.isInstance(bean));
    }

    /**
     * Cleanup the scope.
     */
    void clear() {
        singletonByBeanDefinition.clear();
        singletonByArgumentAndQualifier.clear();
    }

    /**
     * The bean definition identity implementation.
     *
     * @author Denis Stepanov
     * @since 3.5.0
     */
    interface BeanDefinitionIdentity {

        static BeanDefinitionIdentity of(BeanDefinition<?> beanDefinition) {
            if (beanDefinition instanceof BeanDefinitionDelegate) {
                return new BeanDefinitionDelegatedIdentity((BeanDefinitionDelegate<?>) beanDefinition);
            }
            if (beanDefinition instanceof NoInjectionBeanDefinition) {
                return new NoInjectionBeanDefinitionIdentity((NoInjectionBeanDefinition<?>) beanDefinition);
            }
            return new SimpleBeanDefinitionIdentity(beanDefinition);
        }

    }

    /**
     * Simple key object that compares {@link BeanDefinitionDelegate}s by its class name and attributes.
     *
     * @since 3.5.0
     */
    static final class BeanDefinitionDelegatedIdentity implements BeanDefinitionIdentity {

        private final BeanDefinitionDelegate<?> beanDefinitionDelegate;

        BeanDefinitionDelegatedIdentity(BeanDefinitionDelegate<?> beanDefinitionDelegate) {
            this.beanDefinitionDelegate = beanDefinitionDelegate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BeanDefinitionDelegatedIdentity that = (BeanDefinitionDelegatedIdentity) o;
            if (beanDefinitionDelegate.definition.getClass() != that.beanDefinitionDelegate.definition.getClass()) {
                return false;
            }
            return Objects.equals(beanDefinitionDelegate.getAttributes(), that.beanDefinitionDelegate.getAttributes())
                    && Objects.equals(beanDefinitionDelegate.getQualifier(), that.beanDefinitionDelegate.getQualifier());
        }

        @Override
        public int hashCode() {
            return beanDefinitionDelegate.definition.hashCode();
        }
    }

    /**
     * Simple key object that compares {@link BeanDefinitionDelegate}s by its class name and attributes.
     *
     * @since 3.5.0
     */
    static final class NoInjectionBeanDefinitionIdentity implements BeanDefinitionIdentity {

        private final NoInjectionBeanDefinition<?> beanDefinition;

        NoInjectionBeanDefinitionIdentity(NoInjectionBeanDefinition<?> beanDefinition) {
            this.beanDefinition = beanDefinition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NoInjectionBeanDefinitionIdentity that = (NoInjectionBeanDefinitionIdentity) o;
            if (beanDefinition.getBeanType() != that.beanDefinition.getBeanType()) {
                return false;
            }
            Qualifier<?> qualifier = beanDefinition.getQualifier();
            Qualifier<?> thatQualifier = that.beanDefinition.getQualifier();
            if (qualifier == thatQualifier) {
                return true;
            }
            return qualifier != null && qualifier.equals(thatQualifier);
        }

        @Override
        public int hashCode() {
            return beanDefinition.getBeanType().hashCode();
        }
    }

    /**
     * Simple key object that compares {@link BeanDefinition}s by its class name.
     * It's possible we have multiple instances of the same bean definition.
     *
     * @since 3.5.0
     */
    static final class SimpleBeanDefinitionIdentity implements BeanDefinitionIdentity {

        private final Class<?> beanDefinitionClass;

        SimpleBeanDefinitionIdentity(BeanDefinition<?> beanDefinition) {
            this.beanDefinitionClass = beanDefinition.getClass();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SimpleBeanDefinitionIdentity that = (SimpleBeanDefinitionIdentity) o;
            return beanDefinitionClass == that.beanDefinitionClass;
        }

        @Override
        public int hashCode() {
            return beanDefinitionClass.hashCode();
        }
    }

}
