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
import io.micronaut.inject.InstantiatableBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Intercepted {@link InstantiatableBeanDefinition} that carries constructor interceptor metadata
 * for runtime proxy beans.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public interface ProxyInterceptedInstantiateBeanDefinition<T> extends InterceptedInstantiateBeanDefinition<T> {

    @Override
    default T instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
        @Nullable Object[] constructorValues = resolveInstantiationValues(resolutionContext, context);
        List<BeanRegistration<Interceptor<T, T>>> interceptors = (List) constructorValues[constructorValues.length - 2];
        return ConstructorInterceptorChain.instantiate(
            resolutionContext,
            context,
            interceptors,
            this,
            new InterceptedBeanConstructor<>(this, resolutionContext, context),
            5,
            constructorValues
        );
    }
}
