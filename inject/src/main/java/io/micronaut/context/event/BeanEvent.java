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
package io.micronaut.context.event;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An abstract bean event.
 *
 * <p>Every bean event reports the dependent beans of its bean, the beans created for it alone, among them the
 * non-singleton interceptors that intercept it, which is how a listener finds the interceptors of a bean:</p>
 *
 * <table>
 * <caption>What each event reports</caption>
 * <tr><th>Event</th><th>{@link #getBeanRegistration()}</th><th>{@link #getDependentBeans()}</th></tr>
 * <tr><td>{@link BeanInitializingEvent}</td><td>{@code null}</td><td>the dependents created so far</td></tr>
 * <tr><td>{@link BeanCreatedEvent}</td><td>{@code null}</td><td>the dependents the bean was created with</td></tr>
 * <tr><td>{@link BeanPreDestroyEvent}</td><td>the registration</td><td>its dependents, alive</td></tr>
 * <tr><td>{@link BeanDestroyedEvent}</td><td>the registration it was destroyed through</td><td>the dependents
 * destroyed with it</td></tr>
 * </table>
 *
 * <p>The context builds the registration of a bean once the bean is created, so the two creation events have
 * none.</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class BeanEvent<T> extends BeanContextEvent {

    protected final BeanDefinition<T> beanDefinition;
    protected final T bean;
    private final transient @Nullable BeanRegistration<T> beanRegistration;
    private final transient List<BeanRegistration<?>> dependentBeans;

    /**
     * @param beanContext    The bean context
     * @param beanDefinition The bean definition
     * @param bean           The bean
     */
    public BeanEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        this(beanContext, beanDefinition, bean, null, null);
    }

    /**
     * @param beanContext      The bean context
     * @param beanDefinition   The bean definition
     * @param bean             The bean
     * @param beanRegistration The registration of the bean, or {@code null} while the bean is being created
     * @param dependentBeans   The dependent beans of the bean, see {@link #getDependentBeans()}
     * @since 5.3.0
     */
    @Internal
    protected BeanEvent(BeanContext beanContext,
                        BeanDefinition<T> beanDefinition,
                        T bean,
                        @Nullable BeanRegistration<T> beanRegistration,
                        @Nullable List<BeanRegistration<?>> dependentBeans) {
        super(beanContext);
        this.beanDefinition = beanDefinition;
        this.bean = bean;
        this.beanRegistration = beanRegistration;
        this.dependentBeans = dependentBeans == null ? List.of() : List.copyOf(dependentBeans);
    }

    /**
     * @return The bean that was created
     */
    public T getBean() {
        return bean;
    }

    /**
     * @return The bean definition
     */
    public BeanDefinition<T> getBeanDefinition() {
        return beanDefinition;
    }

    /**
     * The registration of the bean: for a destruction event, the one the bean is destroyed through.
     *
     * @return The registration, or {@code null} while the bean is being created, or when the event was fired without
     * one
     * @since 5.3.0
     */
    public @Nullable BeanRegistration<T> getBeanRegistration() {
        return beanRegistration;
    }

    /**
     * The dependent beans of the bean, in the order they were created: the beans created for it alone, among them
     * the non-singleton interceptors that intercept it. See the table on {@link BeanEvent} for what each event
     * reports.
     *
     * @return The dependent beans, never {@code null}
     * @since 5.3.0
     */
    public List<BeanRegistration<?>> getDependentBeans() {
        return dependentBeans;
    }
}
