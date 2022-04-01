/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.exceptions.BeanDestructionException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DisposableBeanDefinition;

import java.util.List;
import java.util.ListIterator;

/**
 * The disposing bean registration.
 *
 * @param <BT> The bean type
 * @since 3.5.0
 */
final class BeanDisposingRegistration<BT> extends BeanRegistration<BT> {
    private final BeanContext beanContext;
    private final List<BeanRegistration<?>> dependents;
    private final boolean hasDependents;

    BeanDisposingRegistration(BeanContext beanContext, DefaultBeanContext.BeanKey<BT> key, BeanDefinition<BT> beanDefinition, BT createdBean, List<BeanRegistration<?>> dependents) {
        super(key, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = dependents;
        this.hasDependents = true;
    }

    BeanDisposingRegistration(BeanContext beanContext, DefaultBeanContext.BeanKey<BT> key, BeanDefinition<BT> beanDefinition, BT createdBean) {
        super(key, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.hasDependents = false;
        this.dependents = null;
    }

    @Override
    public void close() {
        final BeanDefinition<BT> definition = definition();
        final BT beanToDestroy = getBean();
        if (definition instanceof DisposableBeanDefinition) {
            ((DisposableBeanDefinition<BT>) definition).dispose(beanContext, beanToDestroy);
        }
        if (beanToDestroy instanceof LifeCycle) {
            try {
                ((LifeCycle) beanToDestroy).stop();
            } catch (Exception e) {
                throw new BeanDestructionException(definition, e);
            }
        }
        if (hasDependents) {
            final ListIterator<BeanRegistration<?>> i = dependents.listIterator(dependents.size());
            while (i.hasPrevious()) {
                final BeanRegistration<?> dependent = i.previous();
                final Object bean = dependent.getBean();
                final BeanDefinition<?> beanDefinition = dependent.getBeanDefinition();
                if (beanDefinition instanceof DisposableBeanDefinition) {
                    try {
                        //noinspection unchecked
                        ((DisposableBeanDefinition) beanDefinition).dispose(beanContext, bean);
                    } catch (Exception e) {
                        if (DefaultBeanContext.LOG.isErrorEnabled()) {
                            DefaultBeanContext.LOG.error("Error disposing dependent bean of type " + beanDefinition.getBeanType().getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        }
    }
}
