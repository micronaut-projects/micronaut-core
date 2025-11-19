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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.DisposableBeanDefinition;

import java.util.List;
import java.util.Objects;

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

    /**
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     */
    public BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean) {
        this.identifier = identifier;
        this.beanDefinition = beanDefinition;
        this.bean = bean;
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
    @NonNull
    public static <K> BeanRegistration<K> of(@NonNull BeanContext beanContext,
                                             @NonNull BeanIdentifier identifier,
                                             @NonNull BeanDefinition<K> beanDefinition,
                                             @NonNull K bean) {
        return of(beanContext, identifier, beanDefinition, bean, null);
    }

    /**
     * Creates new bean registration. Possibly disposing registration can be returned.
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
    @NonNull
    public static <K> BeanRegistration<K> of(@NonNull BeanContext beanContext,
                                             @NonNull BeanIdentifier identifier,
                                             @NonNull BeanDefinition<K> beanDefinition,
                                             @NonNull K bean,
                                             @Nullable
                                             List<BeanRegistration<?>> dependents) {
        boolean hasDependents = CollectionUtils.isNotEmpty(dependents);
        if (beanDefinition instanceof DisposableBeanDefinition || bean instanceof LifeCycle || hasDependents) {
            return hasDependents ?
                new BeanDisposingRegistration<>(beanContext, identifier, beanDefinition, bean, dependents) :
                new BeanDisposingRegistration<>(beanContext, identifier, beanDefinition, bean);
        }
        return new BeanRegistration<>(identifier, beanDefinition, bean);
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

    @NonNull
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
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return definition().isEnabled(context, resolutionContext);
    }

    @Override
    public Class<T> getBeanType() {
        return definition().getBeanType();
    }
}
