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
package io.micronaut.aop.beandefinition;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.ConstructorInterceptorChain;
import io.micronaut.aop.chain.SharedInterceptorRegistrations;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.InstantiatableBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Intercepted {@link InstantiatableBeanDefinition}.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
public interface InterceptedBeanDefinition<T> extends InstantiatableBeanDefinition<T> {

    /**
     * Resolve the construction values.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @return the construction values
     */
    @Nullable Object[] resolveInstantiationValues(BeanResolutionContext resolutionContext, BeanContext context);

    @Override
    default T instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
        InterceptedConstructor<T> constructor = new InterceptedConstructor<>(this, resolutionContext, context);
        // Resolve the constructor values first, as before, so that resolving interceptors cannot change the order in
        // which this bean's own dependencies are created.
        Object[] values = resolveInstantiationValues(resolutionContext, context);
        // One resolution for construction, post-construct and pre-destroy rather than one per interception point, so a
        // non-singleton interceptor is shared by every phase of this bean.
        List<BeanRegistration<Interceptor<T, T>>> interceptors =
            SharedInterceptorRegistrations.resolve(resolutionContext, this, constructor);
        SharedInterceptorRegistrations.push(resolutionContext, this, interceptors);
        try {
            return ConstructorInterceptorChain.instantiate(
                resolutionContext,
                context,
                interceptors,
                this,
                constructor,
                values
            );
        } finally {
            SharedInterceptorRegistrations.pop(resolutionContext, this, interceptors);
        }
    }

    /**
     * The original {@link #instantiate(BeanResolutionContext, BeanContext)} call that should be intercepted.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param parameterValues   The construction values
     * @return The intercepted result
     */
    T doInstantiate(BeanResolutionContext resolutionContext, BeanContext context, @Nullable Object[] parameterValues);
}
