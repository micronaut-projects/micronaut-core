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
import java.util.Objects;

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
    @Nullable
    @SuppressWarnings("java:S3077") // set once as the proxy is registered; only its own volatile field is read through it
    private volatile AbstractBeanResolutionContext proxyTargetContext;
    // the interceptors a proxy fronting the bean selected for it, see RegisteredBeanInterceptors
    @SuppressWarnings("java:S3077") // a KeptSelection is immutable, set under the lock of this registration
    @Nullable
    private volatile KeptSelection keptSelection;
    // whether this bean was created as an interceptor of the bean it is a dependent of
    private volatile boolean createdAsInterceptor;

    @SuppressWarnings("unchecked") // Adapt the registration compatibility boundary once.
    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<BeanRegistration<?>> dependents,
                              @Nullable List<?> interceptorRegistrations) {
        this(beanContext, identifier, beanDefinition, createdBean, dependents,
            interceptorRegistrations == null ? InterceptorCandidates.Unresolved.INSTANCE
                : new InterceptorCandidates.Resolved((List<BeanRegistration<?>>) interceptorRegistrations), new BeanDependencies());
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<BeanRegistration<?>> dependents,
                              InterceptorCandidates interceptorCandidates,
                              BeanDependencies dependencies) {
        super(identifier, beanDefinition, createdBean, dependencies);
        this.beanContext = beanContext;
        // A reconstructed proxy wrapper already has its complete owner. Reattaching its retained advice would
        // duplicate registrations or add them back to an owner that was already destroyed.
        if (getDependencies() == dependencies || createdBean instanceof DefaultBeanDependencyResolver) {
            getDependencies().initialize(dependents, interceptorCandidates);
        }
    }

    @Override
    public void close() {
        // Closing and direct context destruction use the same claim before running callbacks.
        if (beanContext instanceof DefaultBeanContext || beginDestruction()) {
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
     * Claims destruction before callbacks run, so that closing or destroying the registration again does nothing.
     *
     * @return {@code true} if the registration had not been closed or marked before
     */
    boolean beginDestruction() {
        return getDependencies().beginDestruction();
    }

    /**
     * Returns the dependents using the legacy nullable contract.
     * @return The dependents, or {@code null} when none are retained
     * @deprecated Use {@link #dependentBeans()}, which returns an empty list when there are no dependents.
     */
    @SuppressWarnings("java:S1133") // Retained for source and binary compatibility with the legacy dependent accessor.
    @Deprecated(since = "5.3.0", forRemoval = false)
    @Nullable
    public List<BeanRegistration<?>> getDependents() {
        List<BeanRegistration<?>> dependents = dependentBeans();
        return dependents.isEmpty() ? null : dependents;
    }

    @Override
    BeanDependencies getDependencies() {
        return Objects.requireNonNull(super.getDependencies());
    }

    /**
     * @return The interceptor candidate state retained while this bean was created
     */
    InterceptorCandidates getInterceptorCandidates() {
        return getDependencies().interceptorCandidates();
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
     * Clears the interceptor selection retained for this registration.
     */
    synchronized void takeSelection() {
        keptSelection = null;
    }

    /**
     * Clears the retained interceptor selection and transfers the owned dependents exactly once.
     * @return The dependents
     * @deprecated Destruction now uses {@link BeanDependencies#close(DefaultBeanContext)} through
     * the registration's dependency owner. Retained for compatibility with older callers in this package.
     */
    @Deprecated(since = "5.3.0", forRemoval = false)
    synchronized List<BeanRegistration<?>> takeDependents() {
        takeSelection();
        return getDependencies().takeDependents();
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
            if (getDependencies().isClosing()) {
                // closed, or being destroyed: nothing becomes the bean's any more
                return null;
            }
            S selection = getDependencies().resolve((DefaultBeanContext) beanContext, getBeanDefinition(), selector);
            keptSelection = new KeptSelection(key, selection);
            return selection;
        }
    }

    private record KeptSelection(Object key, Object value) {
    }
}
