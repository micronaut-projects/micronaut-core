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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Intercepted {@link ParametrizedInstantiatableBeanDefinition} that retains proxy interceptor data.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
public interface ParameterizedProxyBeanDefinition<T>
    extends ParameterizedInterceptedBeanDefinition<T> {

    /**
     * Number of internal constructor parameters appended for runtime proxy construction:
     * intercepted bean, resolution context, bean context, proxy target bean definition, and interceptor registrations.
     */
    int ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS_COUNT = 5;

    @Override
    default T doInstantiate(BeanResolutionContext resolutionContext, BeanContext context, Map<String, Object> requiredArgumentValues) {
        Object[] constructorValues = Objects.requireNonNull(
            resolveInstantiationValues(resolutionContext, context, requiredArgumentValues),
            "Resolved instantiation values cannot be null"
        );
        List<BeanRegistration<Interceptor<T, T>>> interceptors = (List) constructorValues[constructorValues.length - 2];
        return ConstructorInterceptorChain.instantiate(
            resolutionContext,
            context,
            interceptors,
            this,
            new InterceptedParametrizedConstructor<>(this, resolutionContext, context),
            ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS_COUNT,
            constructorValues
        );
    }
}
