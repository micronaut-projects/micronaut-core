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
import io.micronaut.context.BeanResolutionContext;
import org.jspecify.annotations.Nullable;
import io.micronaut.inject.BeanDefinition;

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
    private final @Nullable BeanResolutionContext resolutionContext;

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
    public BeanPreDestroyEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean,
                               @Nullable BeanResolutionContext resolutionContext) {
        super(beanContext, beanDefinition, bean);
        this.resolutionContext = resolutionContext;
    }

    /**
     * Returns the context for resolving temporary destruction dependencies through
     * {@link BeanResolutionContext#withDependencies(java.util.function.Function)}. Its destruction permission
     * is confined to the synchronous container invocation. Events constructed with the original constructor
     * do not carry a resolution context.
     * @return The resolution context, or {@code null} if none was supplied
     * @since 5.3.0
     */
    public @Nullable BeanResolutionContext getResolutionContext() {
        return resolutionContext;
    }
}
