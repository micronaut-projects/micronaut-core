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
import io.micronaut.inject.BeanIdentifier;

/**
 * <p>An event fired when a bean is created and fully initialized.</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @see BeanInitializingEvent
 * @since 1.0
 */
public class BeanCreatedEvent<T> extends BeanEvent<T> {

    private final BeanIdentifier beanIdentifier;

    /**
     * @param beanContext    The bean context
     * @param beanDefinition The bean definition
     * @param beanIdentifier The bean identifier
     * @param bean           The bean
     */
    public BeanCreatedEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, BeanIdentifier beanIdentifier, T bean) {
        super(beanContext, beanDefinition, bean);
        this.beanIdentifier = beanIdentifier;
    }

    /**
     * @return The bean identifier used to create the bean
     */
    public BeanIdentifier getBeanIdentifier() {
        return beanIdentifier;
    }
}
