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
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.inject.BeanDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * <p>An event fired when a bean is about to be destroyed but before any {@link jakarta.annotation.PreDestroy} methods are invoked..</p>
 *
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @see BeanDestroyedEvent
 * @since 3.0.0
 */
public class BeanPreDestroyEvent<T> extends BeanEvent<T> {
    private final transient @Nullable BeanResolutionContext resolutionContext;
    private transient @Nullable List<Runnable> afterPreDestroyActions;

    /**
     * @param beanContext    The bean context
     * @param beanDefinition The bean definition
     * @param bean           The bean
     */
    public BeanPreDestroyEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        this(beanContext, beanDefinition, bean, null);
    }

    /**
     * @param beanContext The bean context
     * @param beanDefinition The bean definition
     * @param bean The bean
     * @param resolutionContext The resolution context for this destruction invocation
     * @since 5.3.0
     */
    @Internal
    public BeanPreDestroyEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean,
                               @Nullable BeanResolutionContext resolutionContext) {
        super(beanContext, beanDefinition, bean);
        this.resolutionContext = resolutionContext;
    }

    /**
     * Resolves dependencies the listener needs while the bean is being destroyed and releases them when the
     * action returns, including when it fails. The lookups are allowed during context shutdown, only on the
     * thread delivering this event and until the listener returns. The result must not retain an owned
     * dependency, since it has already been destroyed.
     *
     * @param action The invocation
     * @param <R> The result type
     * @return The result
     * @since 5.3.0
     */
    @Experimental
    public <R> R withDependencies(Function<BeanDependencyGroup, R> action) {
        return resolutionContext != null ? resolutionContext.withDependencies(action) : getSource().withDependencies(action);
    }

    /**
     * Registers an action the context runs once the bean's {@link jakarta.annotation.PreDestroy} methods have been
     * invoked and before the dependent beans created with it are destroyed: the step a container that closes a bean
     * after its pre-destroy callbacks, while what it depends on is still alive, runs in.
     *
     * <p>The actions run in the order they were registered, on the thread destroying the bean and after every
     * pre-destroy listener has returned, so the dependencies resolved through {@link #withDependencies(Function)}
     * are no longer available to them. A runtime exception an action throws fails the destruction as one a
     * listener throws does, after the remaining actions ran; the dependents are released either way. An action
     * registered on an event the context did not deliver is never run.</p>
     *
     * @param action The action
     * @since 5.3.0
     */
    @Experimental
    public void afterPreDestroy(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (afterPreDestroyActions == null) {
            afterPreDestroyActions = new ArrayList<>(1);
        }
        afterPreDestroyActions.add(action);
    }

    /**
     * @return The actions registered with {@link #afterPreDestroy(Runnable)}, in the order of registration
     * @since 5.3.0
     */
    @Internal
    public List<Runnable> getAfterPreDestroyActions() {
        return afterPreDestroyActions == null ? List.of() : afterPreDestroyActions;
    }
}
