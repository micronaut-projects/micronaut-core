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
    private final BeanDependencies dependencies;
    @Nullable
    @SuppressWarnings("java:S3077") // set once as the proxy is registered; only its own volatile field is read through it
    private volatile AbstractBeanResolutionContext proxyTargetContext;
    @Nullable
    private final List<?> interceptorRegistrations;
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
                              @Nullable List<BeanRegistration<?>> dependents,
                              @Nullable List<?> interceptorRegistrations) {
        this(beanContext, identifier, beanDefinition, createdBean, dependents, interceptorRegistrations, new BeanDependencies());
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<BeanRegistration<?>> dependents,
                              @Nullable List<?> interceptorRegistrations,
                              BeanDependencies dependencies) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        // The resolver is a dependent bean itself. Its registration holds the same ownership state, so normal
        // destruction and shutdown graph traversal need no resolver-specific path.
        this.dependencies = createdBean instanceof DefaultBeanDependencyResolver resolver
            ? resolver.dependencies : dependencies;
        this.dependencies.initialize(dependents, interceptorRegistrations);
        this.interceptorRegistrations = interceptorRegistrations;
    }

    @Override
    public void close() {
        // idempotent, as AutoCloseable asks an implementation to be: destroying a bean runs its pre-destroy
        // listeners, its @PreDestroy and its disposer, and a registration closed twice — by a
        // try-with-resources and an explicit close, or by two owners that each believe they hold it — must
        // not run them twice
        if (markDestroyed()) {
            beanContext.destroyBean(this);
        }
    }

    /**
     * @return The resolution context a lazy proxy retains to resolve its target, or {@code null} if the bean is
     * not such a proxy
     */
    @Nullable
    AbstractBeanResolutionContext getProxyTargetContext() {
        return proxyTargetContext;
    }

    void setProxyTargetContext(@Nullable AbstractBeanResolutionContext proxyTargetContext) {
        this.proxyTargetContext = proxyTargetContext;
    }

    /**
     * Marks the registration as destroyed, so that closing it afterwards does not destroy the bean again.
     *
     * @return {@code true} if the registration had not been closed or marked before
     */
    boolean markDestroyed() {
        return dependencies.markDestroyed();
    }

    BeanDependencies getDependencies() {
        return dependencies;
    }

    @Override
    public List<BeanRegistration<?>> dependentBeans() {
        return dependencies.dependentBeans();
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
     * Stops resolution and transfers the dependents to the destruction caller exactly once.
     *
     * @return The dependents
     */
    synchronized void takeSelection() {
        keptSelection = null;
    }

    synchronized List<BeanRegistration<?>> takeDependents() {
        takeSelection();
        return dependencies.takeDependents();
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
            if (dependencies.isClosing()) {
                // closed, or being destroyed: nothing becomes the bean's any more
                return null;
            }
            S selection = dependencies.resolve((DefaultBeanContext) beanContext, getBeanDefinition(), selector::apply);
            keptSelection = new KeptSelection(key, selection);
            return selection;
        }
    }

    private record KeptSelection(Object key, Object value) {
    }
}
