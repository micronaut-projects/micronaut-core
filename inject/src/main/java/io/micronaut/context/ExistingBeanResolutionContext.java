/*
 * Copyright 2017-2026 original authors
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The resolution context of a bean that exists already, through which a proxy fronting the bean resolves the
 * interceptors of the bean as the bean's own.
 *
 * <p>An interceptor no scope holds, a prototype or one with no scope, is the bean's own: the instance created for the
 * bean earlier, while it was created or by an earlier selection, is found among the dependents of its registration and
 * used again, and one created now is a dependent of this context, which the registration takes over, so that it is
 * destroyed with the bean. A singleton, or an interceptor of a custom scope, comes from its scope as always.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ExistingBeanResolutionContext extends AbstractBeanResolutionContext {

    private final Map<BeanIdentifier, BeanRegistration<?>> beansInCreation = new ConcurrentHashMap<>(5);
    private final BeanDisposingRegistration<?> bean;

    /**
     * @param context The bean context
     * @param bean    The registration of the bean
     */
    ExistingBeanResolutionContext(BeanContext context, BeanDisposingRegistration<?> bean) {
        super((DefaultBeanContext) context, bean.getBeanDefinition());
        this.bean = bean;
    }

    @Nullable
    @Override
    <I> BeanRegistration<I> findInterceptor(BeanDefinition<I> definition) {
        BeanRegistration<I> created = super.findInterceptor(definition);
        if (created != null || !getPath().isEmpty()) {
            // a bean created on the way, such as a dependency of a new interceptor, has interceptors of its own
            return created;
        }
        return findInterceptor(bean.getDependents(), definition);
    }

    @Override
    public BeanResolutionContext copy() {
        DefaultBeanResolutionContext copy = new DefaultBeanResolutionContext(context, rootDefinition);
        copy.copyStateFrom(this);
        return copy;
    }

    @Override
    public void close() {
        beansInCreation.clear();
    }

    @Override
    public <T> void addInFlightBean(BeanIdentifier beanIdentifier, BeanRegistration<T> beanRegistration) {
        beansInCreation.put(beanIdentifier, beanRegistration);
    }

    @Override
    public void removeInFlightBean(BeanIdentifier beanIdentifier) {
        beansInCreation.remove(beanIdentifier);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> BeanRegistration<T> getInFlightBean(BeanIdentifier beanIdentifier) {
        return (BeanRegistration<T>) beansInCreation.get(beanIdentifier);
    }
}
