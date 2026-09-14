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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;

import java.util.ArrayList;
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
    private final int order;
    @Nullable
    private List<BeanRegistration<?>> dependents;
    @Nullable
    private volatile Map<Object, Object> dependentState;
    private volatile boolean inDependentScope;

    /**
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     */
    public BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean) {
        this(identifier, beanDefinition, bean, null);
    }

    /**
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     * @param dependents     The beans created for the bean, or {@code null}
     */
    BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean, @Nullable List<BeanRegistration<?>> dependents) {
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
     * the bean, which are of the bean's dependent scope. A registration built any other way has none.</p>
     *
     * @return The dependent beans, never {@code null}
     * @since 5.3.0
     */
    public synchronized List<BeanRegistration<?>> getDependentBeans() {
        return dependents == null ? List.of() : List.copyOf(dependents);
    }

    /**
     * Returns state another component keeps against this registration, computing it once.
     *
     * <p>A generated proxy keeps the interceptors it selected for the methods of this bean here, keyed by the proxy
     * class, so that every call through any proxy of the same class fronting this bean selects once.</p>
     *
     * @param key      The key, compared by identity
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
     * Marks this bean as a member of the dependent scope of the bean it was created for: resolved through that
     * bean's {@link DependentBeanContext}, and so the one instance of its definition that bean has there. A
     * dependency injected into the bean is a dependent too, but not a member: an interceptor injected into a bean
     * is not the instance that intercepts it.
     */
    void markInDependentScope() {
        inDependentScope = true;
    }

    /**
     * @return Whether this bean is a member of the dependent scope of the bean it was created for
     */
    boolean isInDependentScope() {
        return inDependentScope;
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
