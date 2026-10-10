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
    // whether this bean was created as an interceptor of the bean it is a dependent of
    private volatile boolean createdAsInterceptor;
    /**
     * The instance the {@link io.micronaut.context.event.BeanCreatedEventListener}s received, when they replaced it
     * and the context records its dependency graph: a development context retains it across a restart and wraps it
     * again with the next context's listeners. Set before the registration is published.
     */
    @Nullable
    private Object beforeListeners;

    @SuppressWarnings("unchecked") // Adapt the registration compatibility boundary once.
    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<BeanRegistration<?>> dependents,
                              @Nullable List<?> interceptorRegistrations) {
        this(beanContext, identifier, beanDefinition, createdBean, dependents,
            interceptorRegistrations == null ? InterceptorCandidates.Unresolved.INSTANCE
                : new InterceptorCandidates.Resolved((List<BeanRegistration<?>>) interceptorRegistrations), new DefaultBeanDependencies());
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<BeanRegistration<?>> dependents,
                              InterceptorCandidates interceptorCandidates,
                              DefaultBeanDependencies dependencies) {
        super(identifier, beanDefinition, createdBean, dependencies);
        this.beanContext = beanContext;
        // A reconstructed proxy wrapper already has its complete owner. Reattaching its retained advice would
        // duplicate registrations or add them back to an owner that was already destroyed.
        if ((getDependencies() == dependencies || createdBean instanceof DefaultBeanDependencyResolver)
            && !getDependencies().initialize(dependents, interceptorCandidates)
            && beanContext instanceof DefaultBeanContext context) {
            // The owner was destroyed before its creation completed and released what it held then. Dependents
            // created with the bean would be attached to an owner that no longer destroys anything.
            context.destroyCreatedBeans(dependents, null);
        }
    }

    @Override
    public void close() {
        // Closing and direct context destruction share one claim, so the callbacks run once. The default context
        // takes the claim itself as it destroys the registration; for any other context it is taken here.
        boolean claimedByContext = beanContext instanceof DefaultBeanContext;
        if (claimedByContext || beginDestruction()) {
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

    @Override
    public List<BeanRegistration<?>> dependentBeans() {
        return super.dependentBeans();
    }

    @Override
    DefaultBeanDependencies getDependencies() {
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
     * @return The instance the bean created listeners received, when they replaced it and the context records it
     */
    @Nullable
    Object getBeforeListeners() {
        return beforeListeners;
    }

    void setBeforeListeners(@Nullable Object beforeListeners) {
        this.beforeListeners = beforeListeners;
    }
}
