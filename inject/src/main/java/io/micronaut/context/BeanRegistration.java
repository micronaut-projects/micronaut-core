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
import io.micronaut.inject.proxy.InterceptedBeanProxy;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;

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
    private final @Nullable DefaultBeanDependencies dependencies;

    /**
     * @param identifier     The bean identifier
     * @param beanDefinition The bean definition
     * @param bean           The bean instance
     */
    public BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean) {
        this(identifier, beanDefinition, bean, null);
    }

    /**
     * Creates a registration that holds the given dependency owner.
     *
     * <p>A registration wrapped around a dependency resolver or a generated proxy takes the owner that bean already
     * carries instead, so that a caller holding only the instance closes the same dependents.</p>
     *
     * @param identifier The bean identifier
     * @param beanDefinition The bean definition
     * @param bean The bean instance
     * @param dependencies The owner created with the bean, or null for a registration the container does not own
     */
    BeanRegistration(BeanIdentifier identifier, BeanDefinition<T> beanDefinition, T bean,
                     @Nullable DefaultBeanDependencies dependencies) {
        // A wrapper around a retained proxy or resolver shares its original owner, including closure state.
        if (bean instanceof DefaultBeanDependencyResolver resolver) {
            this.dependencies = resolver.dependencies;
        } else if (bean instanceof InterceptedBeanProxy<?> proxy
            && proxy.$beanDependencies() instanceof DefaultBeanDependencyResolver resolver) {
            this.dependencies = resolver.dependencies;
        } else {
            this.dependencies = dependencies;
        }
        this.identifier = identifier;
        this.beanDefinition = beanDefinition;
        this.bean = bean;
        this.order = getOrder(beanDefinition, bean);
    }

    private static int getOrder(@Nullable BeanDefinition<?> beanDefinition, @Nullable Object bean) {
        // Preserve ordering for legacy callers that construct registrations without a bean definition.
        if (beanDefinition == null) {
            return bean == null ? 0 : OrderUtil.getOrder(bean);
        }
        if (bean instanceof Ordered ordered) {
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
        return of(beanContext, identifier, beanDefinition, bean, dependents, null);
    }

    /**
     * Creates a bean registration with optional dependent and lifecycle interceptor registrations.
     *
     * <p>This overload is package-private because the interceptor registrations are an internal bean-creation detail.</p>
     */
    static <K> BeanRegistration<K> of(BeanContext beanContext,
                                      BeanIdentifier identifier,
                                      BeanDefinition<K> beanDefinition,
                                      K bean,
                                      @Nullable List<BeanRegistration<?>> dependents,
                                      @Nullable List<?> interceptorRegistrations) {
        return new BeanDisposingRegistration<>(beanContext, identifier, beanDefinition, bean, dependents, interceptorRegistrations);
    }

    /**
     * @return The owner of what was created for this bean, or null for a registration the container does not own
     */
    @Nullable
    DefaultBeanDependencies getDependencies() {
        return dependencies;
    }

    /**
     * Returns the dependencies of this bean instance, through which a caller outside this module creates
     * something that is destroyed with the bean.
     *
     * @return The dependencies, or null for a registration the container does not own
     * @since 5.3.0
     */
    @Internal
    public @Nullable BeanDependencies dependencies() {
        return dependencies;
    }

    /**
     * @return An immutable snapshot of the dependents the owner of this registration holds, empty without an owner
     */
    List<BeanRegistration<?>> dependentBeans() {
        return dependencies == null ? List.of() : dependencies.dependentBeans();
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
