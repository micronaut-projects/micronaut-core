/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The disposing bean registration.
 *
 * @param <BT> The bean type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class BeanDisposingRegistration<BT> extends BeanRegistration<BT> implements DependentBeanProvider {
    private final BeanContext beanContext;
    private final java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();
    // replaced, never modified, under the lock of this registration: a proxy fronting the bean may add to them after
    // the bean was created, while they are read without the lock
    @SuppressWarnings("java:S3077") // a published list is never modified, volatile only publishes it
    @Nullable
    private volatile List<BeanRegistration<?>> dependents;
    @Nullable
    private final List<?> interceptorRegistrations;
    // guarded by this: once set, nothing becomes the bean's any more
    private boolean destroyed;
    // the interceptors a proxy fronting the bean selected for it, see RegisteredBeanInterceptors
    @SuppressWarnings("java:S3077") // a KeptSelection is immutable, set under the lock of this registration
    @Nullable
    private volatile KeptSelection keptSelection;
    // whether this bean was created as an interceptor of the bean it is a dependent of
    private volatile boolean createdAsInterceptor;

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              List<BeanRegistration<?>> dependents,
                              @Nullable List<?> interceptorRegistrations) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = dependents;
        this.interceptorRegistrations = interceptorRegistrations;
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<?> interceptorRegistrations) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = null;
        this.interceptorRegistrations = interceptorRegistrations;
    }

    @Override
    public void close() {
        // idempotent, as AutoCloseable asks an implementation to be: destroying a bean runs its pre-destroy
        // listeners, its @PreDestroy and its disposer, and a registration closed twice — by a
        // try-with-resources and an explicit close, or by two owners that each believe they hold it — must
        // not run them twice
        if (closed.compareAndSet(false, true)) {
            beanContext.destroyBean(this);
        }
    }

    @Nullable
    public List<BeanRegistration<?>> getDependents() {
        return dependents;
    }

    @Override
    public List<BeanRegistration<?>> dependentBeans() {
        return dependents == null ? List.of() : List.copyOf(dependents);
    }

    /**
     * @return The interceptor registrations selected while this bean was created, or {@code null}
     */
    @Nullable
    List<?> getInterceptorRegistrations() {
        return interceptorRegistrations;
    }

    /**
     * Marks this bean as created to intercept the bean it is a dependent of, so that every interception point of
     * that bean finds it again, see {@link BeanResolutionContext#getInterceptorRegistrations}. An interceptor a bean
     * injects is a dependent of it too, but carries no mark.
     */
    void markCreatedAsInterceptor() {
        createdAsInterceptor = true;
    }

    /**
     * @return Whether this bean was created to intercept the bean it is a dependent of
     */
    boolean isCreatedAsInterceptor() {
        return createdAsInterceptor;
    }

    /**
     * Adds beans created for this bean after it was created, so that they are destroyed with it.
     *
     * @param created The registrations of the beans
     * @return {@code false} when this bean is destroyed already, and nothing was added
     */
    synchronized boolean addDependents(List<BeanRegistration<?>> created) {
        if (destroyed) {
            return false;
        }
        if (!created.isEmpty()) {
            List<BeanRegistration<?>> current = dependents;
            List<BeanRegistration<?>> added = current == null ? new ArrayList<>(created.size()) : new ArrayList<>(current);
            added.addAll(created);
            dependents = added;
        }
        return true;
    }

    /**
     * Takes the dependents of this bean for destruction: nothing becomes the bean's afterwards.
     *
     * @return The dependents, or {@code null}
     */
    @Nullable
    synchronized List<BeanRegistration<?>> takeDependents() {
        destroyed = true;
        keptSelection = null;
        return dependents;
    }

    /**
     * @param key The key
     * @return The selection kept for the key, or {@code null}
     */
    @Nullable
    Object keptSelection(Object key) {
        KeptSelection kept = keptSelection;
        return kept != null && kept.key == key ? kept.value : null;
    }

    /**
     * Returns the selection kept for the given key, or computes it through a resolution context of this bean and
     * keeps it, see {@link RegisteredBeanInterceptors#select(BeanRegistration, Object, java.util.function.Function)}.
     *
     * @param key      The key
     * @param selector Computes the selection
     * @param <S>      The selection type
     * @return The selection, or {@code null} when this bean is destroyed
     */
    @SuppressWarnings("unchecked")
    @Nullable
    <S> S select(Object key, java.util.function.Function<BeanResolutionContext, S> selector) {
        KeptSelection kept = keptSelection;
        if (kept != null && kept.key == key) {
            return (S) kept.value;
        }
        synchronized (this) {
            kept = keptSelection;
            if (kept != null && kept.key == key) {
                return (S) kept.value;
            }
            if (destroyed) {
                return null;
            }
            S selection;
            try (ExistingBeanResolutionContext resolutionContext = new ExistingBeanResolutionContext(beanContext, this)) {
                try {
                    selection = selector.apply(resolutionContext);
                } catch (RuntimeException | Error e) {
                    // what the selection created before it failed is not the bean's, and nothing else holds it
                    resolutionContext.context.destroyCreatedBeans(resolutionContext.getAndResetDependentBeans(), e);
                    throw e;
                }
                addDependents(resolutionContext.getAndResetDependentBeans());
            }
            keptSelection = new KeptSelection(key, selection);
            return selection;
        }
    }

    private record KeptSelection(Object key, Object value) {
    }
}
