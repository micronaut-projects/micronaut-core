/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Default chain factory. Candidates the caller does not supply are acquired through the registry's
 * {@link InterceptorRegistry#candidateResolver() candidate resolver}, and selected by the registry.
 * Subclasses override a build method to decorate or replace the invocation it returns.
 *
 * @since 5.3.0
 */
@Internal
public class DefaultInterceptorChainFactory implements InterceptorChainFactory {
    private final InterceptorRegistry registry;

    /**
     * @param registry The interceptor registry the invocations are selected by
     */
    public DefaultInterceptorChainFactory(InterceptorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T, R> MethodInvocationContext<T, R> buildMethodChain(
        T target, ExecutableMethod<T, R> method, Interceptor<T, R>[] interceptors, @Nullable Object... parameters) {
        return new MethodInterceptorChain<>(interceptors, target, method, parameters);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T, R> LifecycleInvocation<T, R> buildLifecycleChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        ExecutableMethod<T, R> method,
        T bean,
        InterceptorKind kind,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> candidates) {
        Collection<BeanRegistration<Interceptor<?, ?>>> resolved = candidates;
        if (resolved == null) {
            resolved = registry.candidateResolver().resolveLifecycleCandidates(resolutionContext, definition, method, bean, kind);
        }
        Interceptor<T, R>[] interceptors = (Interceptor[]) registry.resolveMethodInterceptors(method, (Collection) resolved, kind);
        return new MethodInterceptorChain<>(interceptors, bean, method, kind, ArrayUtils.EMPTY_OBJECT_ARRAY);
    }

    @Override
    public <T> ConstructorInvocation<T> buildConstructorChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        BeanConstructor<T> constructor,
        @Nullable Collection<BeanRegistration<Interceptor<T, T>>> candidates,
        int additionalProxyConstructorParametersCount,
        @Nullable Object... parameters) {
        Collection<BeanRegistration<Interceptor<T, T>>> resolved = candidates;
        if (resolved == null) {
            resolved = registry.candidateResolver().resolveConstructorCandidates(resolutionContext, definition, constructor);
        }
        return new ConstructorInterceptorChain<>(definition, constructor,
            registry.resolveConstructorInterceptors(constructor, resolved),
            additionalProxyConstructorParametersCount, parameters);
    }
}
