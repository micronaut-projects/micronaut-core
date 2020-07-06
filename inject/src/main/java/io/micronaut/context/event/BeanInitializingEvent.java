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
import io.micronaut.inject.BeanDefinition;

/**
 * <p>An event fired when a bean's properties have been populated but initialization hooks (such as
 * {@link javax.annotation.PostConstruct} methods) have not yet been triggered.</p>
 * <p>
 * <p>To listen to an event for a fully initialized bean see {@link BeanCreatedEvent}</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @see BeanCreatedEvent
 * @since 1.0
 */
public class BeanInitializingEvent<T> extends BeanEvent<T> {

    /**
     * @param beanContext    The bean context
     * @param beanDefinition The bean definition
     * @param bean           The bean
     */
    public BeanInitializingEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        super(beanContext, beanDefinition, bean);
    }
}
