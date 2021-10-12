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
 * An abstract bean event.
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class BeanEvent<T> extends BeanContextEvent {

    protected final BeanDefinition<T> beanDefinition;
    protected final T bean;

    /**
     * @param beanContext    The bean context
     * @param beanDefinition The bean definition
     * @param bean           The bean
     */
    public BeanEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        super(beanContext);
        this.beanDefinition = beanDefinition;
        this.bean = bean;
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
}
