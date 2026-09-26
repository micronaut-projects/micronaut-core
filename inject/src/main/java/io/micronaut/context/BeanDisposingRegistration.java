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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import java.util.List;
import java.util.Objects;

/**
 * A registration the context created, which destroys its bean through the context when it is closed.
 *
 * @param <BT> The bean type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@SuppressWarnings("removal")
final class BeanDisposingRegistration<BT> extends BeanRegistration<BT> implements DependentBeanProvider {
    private final java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              List<BeanRegistration<?>> dependents) {
        super(beanContext, identifier, beanDefinition, createdBean, dependents);
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean) {
        super(beanContext, identifier, beanDefinition, createdBean, null);
    }

    @Override
    public void close() {
        // idempotent, as AutoCloseable asks an implementation to be: destroying a bean runs its pre-destroy
        // listeners, its @PreDestroy and its disposer, and a registration closed twice — by a
        // try-with-resources and an explicit close, or by two owners that each believe they hold it — must
        // not run them twice
        if (closed.compareAndSet(false, true)) {
            Objects.requireNonNull(beanContext).destroyBean(this);
        }
    }

    @Override
    public List<BeanRegistration<?>> dependentBeans() {
        return getDependentBeans();
    }
}
