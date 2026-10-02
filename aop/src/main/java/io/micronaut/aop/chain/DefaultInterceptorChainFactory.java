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
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Default chain factory using the interceptor registry selected by its bean context.
 * Subclasses can override {@link #buildResolvedInvocation} to customize method and lifecycle invocations
 * after selection, without repeating candidate acquisition or matching.
 *
 * @since 5.3.0
 */
@Internal
@NullMarked
public class DefaultInterceptorChainFactory implements InterceptorChainFactory {
    private final InterceptorRegistry registry;
    private final InterceptorCandidateResolver candidateResolver;

    /**
     * @param registry The context's interceptor registry
     */
    public DefaultInterceptorChainFactory(InterceptorRegistry registry) {
        this.registry = registry;
        this.candidateResolver = new InterceptorCandidateResolver(registry);
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
            resolved = (Collection) resolutionContext.getBeanInterceptors(definition);
        }
        if (resolved == null) {
            resolved = candidateResolver.resolveLifecycleCandidates(resolutionContext, definition, method, bean, kind);
        }
        Interceptor<T, R>[] interceptors = (Interceptor[]) registry.resolveMethodInterceptors(method, (Collection) resolved, kind);
        return buildResolvedInvocation(bean, method, interceptors, kind, ArrayUtils.EMPTY_OBJECT_ARRAY);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T, R> LifecycleInvocation<T, R> buildMethodChain(
        T bean,
        ExecutableMethod<T, R> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> candidates,
        InterceptorKind kind,
        @Nullable Object... parameters) {
        Interceptor<T, ?>[] interceptors = registry.resolveMethodInterceptors(method, candidates, kind);
        return buildResolvedInvocation(bean, method, (Interceptor[]) interceptors, kind, parameters);
    }

    @Override
    public <T> ConstructorInterceptorChain<T> buildConstructorChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        BeanConstructor<T> constructor,
        @Nullable Collection<BeanRegistration<Interceptor<T, T>>> candidates,
        int additionalProxyConstructorParametersCount,
        @Nullable Object... parameters) {
        Collection<BeanRegistration<Interceptor<T, T>>> resolved = candidates;
        if (resolved == null) {
            resolved = candidateResolver.resolveConstructorCandidates(resolutionContext, definition, constructor);
        }
        return new ConstructorInterceptorChain<>(definition, constructor, registry.resolveConstructorInterceptors(constructor, resolved),
            additionalProxyConstructorParametersCount, parameters);
    }
}
