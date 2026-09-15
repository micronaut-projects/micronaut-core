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

import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * <p>A bean registration is an association between a {@link BeanDefinition} and a created bean, typically a
 * {@link jakarta.inject.Singleton}.</p>
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanRegistration<T> implements Ordered, CreatedBean<T>, BeanType<T> {
    final BeanIdentifier identifier;
    final BeanDefinition<T> beanDefinition;
    final T bean;
    // the context that created this registration, or null for one built by hand
    @Nullable
    final BeanContext beanContext;
    private final int order;
    @Nullable
    private List<BeanRegistration<?>> dependents;
    @Nullable
    private volatile Map<Object, Object> dependentState;
    private volatile boolean createdAsInterceptor;

    /**
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     */
    public BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean) {
        this(null, identifier, beanDefinition, bean, null);
    }

    /**
     * @param beanContext    The context that created the bean, or {@code null}
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     * @param dependents     The beans created for the bean, or {@code null}
     */
    BeanRegistration(@Nullable BeanContext beanContext, BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean, @Nullable List<BeanRegistration<?>> dependents) {
        this.beanContext = beanContext;
        this.identifier = identifier;
        this.beanDefinition = beanDefinition;
        this.bean = bean;
        this.dependents = dependents;
        if (bean == null) {
            this.order = beanDefinition == null ? 0 : beanDefinition.getOrder();
        } else {
            this.order = beanDefinition == null ? OrderUtil.getOrder(bean) : getOrder(beanDefinition, bean);
        }
    }

    private static int getOrder(BeanDefinition<?> beanDefinition, Object o) {
        if (o instanceof Ordered ordered) {
            return ordered.getOrder();
        }
        return beanDefinition.getOrder();
    }

    /**
     * Creates new bean registration. Possibly disposing registration can be returned.
     *
     * @param beanContext    The bean context
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     * @param <K>            The bean registration type
     * @return new bean registration
     * @since 3.5.0
     */
    public static <K> BeanRegistration<K> of(BeanContext beanContext,
                                             BeanIdentifier identifier,
                                             BeanDefinition<K> beanDefinition,
                                             K bean) {
        return of(beanContext, identifier, beanDefinition, bean, null);
    }

    /**
     * Creates new bean registration. The returned registration's {@link #close()} destroys the
     * bean through {@link BeanContext#destroyBean(BeanRegistration)}, triggering
     * {@link io.micronaut.context.event.BeanPreDestroyEventListener} and
     * {@link io.micronaut.context.event.BeanDestroyedEventListener} instances even if the bean has
     * no destruction logic of its own.
     *
     * @param beanContext    The bean context
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     * @param dependents     The dependents
     * @param <K>            The bean registration type
     * @return new bean registration
     * @since 3.5.0
     */
    public static <K> BeanRegistration<K> of(BeanContext beanContext,
                                             BeanIdentifier identifier,
                                             BeanDefinition<K> beanDefinition,
                                             K bean,
                                             @Nullable
                                             List<BeanRegistration<?>> dependents) {
        return CollectionUtils.isNotEmpty(dependents) ?
            new BeanDisposingRegistration<>(beanContext, identifier, beanDefinition, bean, Objects.requireNonNull(dependents)) :
            new BeanDisposingRegistration<>(beanContext, identifier, beanDefinition, bean);
    }

    @Override
    public int getOrder() {
        return order;
    }

    /**
     * @return Teh bean identifier
     */
    public BeanIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * @return The bean definition
     */
    public BeanDefinition<T> getBeanDefinition() {
        return beanDefinition;
    }

    /**
     * @return The bean instance
     */
    public T getBean() {
        return bean;
    }

    /**
     * The dependent beans of this bean: the beans created for it, which belong to it alone and are destroyed with
     * it, after its own {@code @PreDestroy}.
     *
     * <p>A registration the context created carries them; among them are the non-singleton interceptors bound to
     * the bean, which are the bean's own. A registration built any other way has none.</p>
     *
     * @return The dependent beans, never {@code null}
     * @since 5.3.0
     */
    public synchronized List<BeanRegistration<?>> getDependentBeans() {
        return dependents == null ? List.of() : List.copyOf(dependents);
    }

    /**
     * Obtains the registrations of the interceptors bound to this bean, as
     * {@link BeanResolutionContext#getInterceptorRegistrations(Argument, Qualifier)} does for a bean being created:
     * a singleton or a custom-scoped interceptor from its scope, any other the instance this bean owns among its
     * dependents, or one created for it now that joins them and is destroyed with it.
     *
     * <p>This is how a proxy that fronts this bean as a separate target selects the interceptors of its methods,
     * and how the bean's pre-destroy interception finds the instances its creation used.</p>
     *
     * @param interceptorType The interceptor type
     * @param binding         The interceptor binding qualifier
     * @param <I>             The interceptor type
     * @return The registrations
     * @throws UnsupportedOperationException If the registration was not created by the bean context
     * @since 5.3.0
     */
    public <I> Collection<BeanRegistration<I>> getInterceptorRegistrations(Argument<I> interceptorType, @Nullable Qualifier<I> binding) {
        // serialised, so that two callers resolving for this bean at once do not each create the interceptor the
        // other is creating
        synchronized (this) {
            try (BeanResolutionContext resolutionContext = newResolutionContext()) {
                return resolutionContext.getInterceptorRegistrations(interceptorType, binding);
            }
        }
    }

    /**
     * Obtains the registration of one interceptor bound to this bean, this bean's own instance of it, as
     * {@link #getInterceptorRegistrations(Argument, Qualifier)} would list it.
     *
     * @param interceptor The interceptor definition
     * @param <I>         The interceptor type
     * @return The registration
     * @throws UnsupportedOperationException If the registration was not created by the bean context
     * @since 5.3.0
     */
    public <I> BeanRegistration<I> getInterceptorRegistration(BeanDefinition<I> interceptor) {
        synchronized (this) {
            try (BeanResolutionContext resolutionContext = newResolutionContext()) {
                return resolutionContext.getInterceptorRegistration(interceptor);
            }
        }
    }

    /**
     * Opens a resolution context for this bean, which exists already: its dependents are the bean's, as they were of
     * the context that created it, and whatever is created through it joins them when it is closed, to be destroyed
     * with the bean. The context must be closed.
     *
     * @return The resolution context
     * @throws UnsupportedOperationException If the registration was not created by the bean context
     */
    BeanResolutionContext newResolutionContext() {
        if (beanContext == null) {
            throw new UnsupportedOperationException("The registration of " + bean + " was not created by the bean context");
        }
        return new ExistingBeanResolutionContext(beanContext, this);
    }

    /**
     * Returns state another component keeps against this registration, computing it once.
     *
     * <p>A proxy fronting this bean keeps the interceptors it selected for the methods of this bean here, keyed by
     * its selector, so that it selects once per target and the selection lives exactly as long as this bean.</p>
     *
     * @param key      The key; a selector does not define equality, so it is compared by identity
     * @param supplier Computes the state when absent
     * @param <S>      The state type
     * @return The state
     * @since 5.3.0
     */
    @Internal
    @SuppressWarnings("unchecked")
    public <S> S dependentState(Object key, Supplier<S> supplier) {
        Map<Object, Object> state = dependentState;
        if (state == null) {
            synchronized (this) {
                state = dependentState;
                if (state == null) {
                    state = new ConcurrentHashMap<>(2);
                    dependentState = state;
                }
            }
        }
        return (S) state.computeIfAbsent(key, ignored -> supplier.get());
    }

    /**
     * Adds a bean created for this bean after this bean itself was created, so that it is destroyed with it.
     *
     * @param registration The registration of the dependent bean
     */
    synchronized void addDependentBean(BeanRegistration<?> registration) {
        if (dependents == null) {
            dependents = new ArrayList<>(2);
        } else if (!(dependents instanceof ArrayList)) {
            // the list created with the bean is unmodifiable; a late dependent needs one of this registration's own
            dependents = new ArrayList<>(dependents);
        }
        dependents.add(registration);
    }

    /**
     * Marks this bean as created for the interception of the bean it is a dependent of, through that bean's
     * {@link BeanResolutionContext#getInterceptorRegistrations(Argument, Qualifier)}, and so the one instance of its
     * definition that bean is intercepted with. A dependency injected into the bean is a dependent too, but carries
     * no mark: an interceptor injected into a bean is not the instance that intercepts it.
     */
    void markCreatedAsInterceptor() {
        createdAsInterceptor = true;
    }

    /**
     * @return Whether this bean was created for the interception of the bean it is a dependent of
     */
    boolean isCreatedAsInterceptor() {
        return createdAsInterceptor;
    }

    @Override
    public String toString() {
        return "BeanRegistration: " + bean;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BeanRegistration<?> that = (BeanRegistration<?>) o;
        return Objects.equals(identifier, that.identifier) &&
                Objects.equals(beanDefinition, that.beanDefinition);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(identifier, beanDefinition);
    }

    @Override
    public BeanDefinition<T> definition() {
        return beanDefinition;
    }

    @Override
    public T bean() {
        return bean;
    }

    @Override
    public BeanIdentifier id() {
        return identifier;
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        return definition().isEnabled(context, resolutionContext);
    }

    @Override
    public Class<T> getBeanType() {
        return definition().getBeanType();
    }
}
