/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

import io.micronaut.inject.BeanDefinition;

import javax.inject.Provider;

/**
 * A default component provider.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class UnresolvedProvider<T> implements Provider<T> {

    private final BeanDefinition<T> beanDefinition;
    private final BeanContext context;
    private T bean;

    /**
     * @param beanDefinition The bean definition
     * @param context  The bean context
     */
    UnresolvedProvider(BeanDefinition<T> beanDefinition, BeanContext context) {
        this.beanDefinition = beanDefinition;
        this.context = context;
    }

    @Override
    public T get() {
        if (beanDefinition.isSingleton()) {
            if (bean == null) { // Allow concurrent access
                bean = context.getBean(beanDefinition);
            }
            return bean;
        }
        return context.getBean(beanDefinition);
    }
}
