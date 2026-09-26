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

import java.util.Map;
import java.util.Objects;
import java.util.List;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanContext;
import io.micronaut.aop.chain.ConstructorInterceptorChain;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.LifecycleInterception;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;

/**
 * Intercepted {@link ParametrizedInstantiatableBeanDefinition} that retains proxy interceptor data.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@Internal
public interface ParameterizedProxyBeanDefinition<T>
    extends ParameterizedInterceptedBeanDefinition<T> {

    /**
     * Number of internal constructor parameters appended to the constructor of a proxy compiled by 5.2: the
     * resolution context, the bean context, the qualifier, the interceptor registrations and the registry.
     *
     * @deprecated Since 5.3.0 a generated proxy appends {@link LifecycleInterception#PROXY_CONSTRUCTOR_PARAMETERS}
     * and implements the parent interface; this one serves proxies compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    int ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS_COUNT = 5;

    /**
     * Instantiates as a proxy compiled by 5.2 expects: the interceptors it was given are the second to last
     * constructor value, and five internal values follow the bean's own.
     */
    @SuppressWarnings({"unchecked", "rawtypes", "removal"})
    @Override
    default T doInstantiate(BeanResolutionContext resolutionContext, BeanContext context, Map<String, Object> requiredArgumentValues) {
        Object[] constructorValues = Objects.requireNonNull(resolveInstantiationValues(resolutionContext, context, requiredArgumentValues), "Resolved instantiation values cannot be null");
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
