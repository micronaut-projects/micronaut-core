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
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Intercepted {@link ParametrizedInstantiatableBeanDefinition}.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public interface InterceptedParametrizedInstantiateBeanDefinition<T> extends ParametrizedInstantiatableBeanDefinition<T> {

    /**
     * Resolves the interceptors that should wrap instantiation.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param constructorValues The constructor argument values
     * @return The interceptors to apply or {@code null} if none
     */
    default @Nullable List<BeanRegistration<Interceptor<T, T>>> resolveInterceptors(BeanResolutionContext resolutionContext, BeanContext context, @Nullable Object[] constructorValues) {
        return null;
    }

    /**
     * Resolve the construction values.
     *
     * @param resolutionContext      The resolution context
     * @param context                The bean context
     * @param requiredArgumentValues The required argument values
     * @return the construction values
     */
    @Nullable Object[] resolveInstantiationValues(BeanResolutionContext resolutionContext, BeanContext context, Map<String, Object> requiredArgumentValues);

    @Override
    default T doInstantiate(BeanResolutionContext resolutionContext, BeanContext context, Map<String, Object> requiredArgumentValues) {
        @Nullable Object[] values = resolveInstantiationValues(resolutionContext, context, requiredArgumentValues);
        return ConstructorInterceptorChain.instantiate(
            resolutionContext,
            context,
            resolveInterceptors(resolutionContext, context, values),
            this,
            new InterceptedParametrizedBeanConstructor<>(this, resolutionContext, context),
            values
        );
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
