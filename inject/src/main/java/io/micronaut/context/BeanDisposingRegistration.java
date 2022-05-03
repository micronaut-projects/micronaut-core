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

/**
 * The disposing bean registration.
 *
 * @param <BT> The bean type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class BeanDisposingRegistration<BT> extends BeanRegistration<BT> {
    private final BeanContext beanContext;
    private final List<BeanRegistration<?>> dependents;

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              List<BeanRegistration<?>> dependents) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = dependents;
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = null;
    }

    @Override
    public void close() {
        beanContext.destroyBean(this);
    }

    public List<BeanRegistration<?>> getDependents() {
        return dependents;
    }
}
