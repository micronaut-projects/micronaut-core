/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context.event;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * <p>An event fired when a bean has been destroyed and all {@link jakarta.annotation.PreDestroy} methods have been invoked.</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @see BeanPreDestroyEvent
 * @since 3.0.0
 */
public class BeanDestroyedEvent<T> extends BeanEvent<T> {

    private final transient @Nullable BeanRegistration<T> beanRegistration;
    private final transient List<BeanRegistration<?>> dependentBeans;

    /**
     * @param beanContext    The bean context
     * @param beanDefinition The bean definition
     * @param bean           The bean
     */
    public BeanDestroyedEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        this(beanContext, beanDefinition, bean, null, List.of());
    }

    /**
     * @param beanContext      The bean context
     * @param beanDefinition   The bean definition
     * @param bean             The bean
     * @param beanRegistration The registration the bean was destroyed through
     * @param dependentBeans   The dependent beans that were destroyed with the bean
     * @since 5.3.0
     */
    public BeanDestroyedEvent(BeanContext beanContext,
                              BeanDefinition<T> beanDefinition,
                              T bean,
                              @Nullable BeanRegistration<T> beanRegistration,
                              @Nullable List<BeanRegistration<?>> dependentBeans) {
        super(beanContext, beanDefinition, bean);
        this.beanRegistration = beanRegistration;
        this.dependentBeans = dependentBeans == null ? List.of() : List.copyOf(dependentBeans);
    }

    /**
     * The registration the bean was destroyed through.
     *
     * <p>The bean is destroyed by now, so the registration no longer lists its dependents: they were destroyed with
     * it and are reported by {@link #getDependentBeans()}.</p>
     *
     * @return The registration, or {@code null} when the event was fired without one
     * @since 5.3.0
     */
    public @Nullable BeanRegistration<T> getBeanRegistration() {
        return beanRegistration;
    }

    /**
     * The dependent beans destroyed with the bean, in the order they were created: the beans created for it alone,
     * among them the non-singleton interceptors that intercepted it. They are destroyed by the time this event is
     * fired, and are what {@link BeanCreatedEvent#getDependentBeans()} reported as the bean was created, together with
     * what was created for the bean afterwards.
     *
     * @return The destroyed dependent beans, never {@code null}
     * @since 5.3.0
     */
    public List<BeanRegistration<?>> getDependentBeans() {
        return dependentBeans;
    }
}
