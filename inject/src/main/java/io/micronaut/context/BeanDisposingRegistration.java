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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The disposing bean registration.
 *
 * @param <BT> The bean type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class BeanDisposingRegistration<BT> extends BeanRegistration<BT> implements DependentBeanProvider {
    private final BeanContext beanContext;
    @Nullable
    private final List<BeanRegistration<?>> dependents;
    @Nullable
    private final List<?> interceptorRegistrations;

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              List<BeanRegistration<?>> dependents,
                              @Nullable List<?> interceptorRegistrations) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = dependents;
        this.interceptorRegistrations = interceptorRegistrations;
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              @Nullable List<?> interceptorRegistrations) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = null;
        this.interceptorRegistrations = interceptorRegistrations;
    }

    @Override
    public void close() {
        beanContext.destroyBean(this);
    }

    @Nullable
    public List<BeanRegistration<?>> getDependents() {
        return dependents;
    }

    @Override
    public List<BeanRegistration<?>> dependentBeans() {
        return dependents == null ? List.of() : List.copyOf(dependents);
    }

    /**
     * @return The interceptor registrations selected while this bean was created, or {@code null}
     */
    @Nullable
    List<?> getInterceptorRegistrations() {
        return interceptorRegistrations;
    }
}
