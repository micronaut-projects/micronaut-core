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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Default chain factory using the interceptor registry selected by its bean context.
 * Subclasses can override {@link #buildResolvedInvocation} and {@link #buildResolvedConstructorInvocation}
 * to customize invocation construction after selection, without repeating acquisition or matching.
 *
 * @since 5.3.0
 */
@Internal
@NullMarked
public class DefaultInterceptorChainFactory implements InterceptorChainFactory {
    private final InterceptorRegistry registry;

    /**
     * @param registry The context's interceptor registry
     */
    public DefaultInterceptorChainFactory(InterceptorRegistry registry) {
        this.registry = registry;
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
            resolved = registry.resolveLifecycleCandidates(resolutionContext, definition, method, bean, kind);
        }
        Interceptor<T, R>[] interceptors = (Interceptor[]) registry.resolveMethodInterceptors(method, (Collection) resolved, kind);
        return buildResolvedInvocation(bean, method, interceptors, kind, ArrayUtils.EMPTY_OBJECT_ARRAY);
    }

    /**
     * Builds a method chain from acquired candidates. Introduction chains also include around advice.
     *
     * @param bean The target
     * @param method The intercepted method
     * @param candidates The interceptor candidates, including an authoritative empty set
     * @param kind The interception kind
     * @param parameters The invocation arguments
     * @param <T> The bean type
     * @param <R> The result type
     * @return A new method chain
     * @since 5.3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T, R> MethodInvocationContext<T, R> buildMethodChain(
        T bean,
        ExecutableMethod<T, R> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> candidates,
        InterceptorKind kind,
        @Nullable Object... parameters) {
        Interceptor<T, ?>[] interceptors = registry.resolveMethodInterceptors(method, candidates, kind);
        return buildResolvedInvocation(bean, method, (Interceptor[]) interceptors, kind, parameters);
    }

    @Override
    public <T, R> LifecycleInvocation<T, R> buildResolvedInvocation(
        T bean, ExecutableMethod<T, R> method, Interceptor<T, R>[] interceptors,
        InterceptorKind kind, @Nullable Object... parameters) {
        return new MethodInterceptorChain<>(interceptors, bean, method, kind, parameters);
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
            resolved = registry.resolveConstructorCandidates(resolutionContext, definition, constructor);
        }
        return buildResolvedConstructorInvocation(definition, constructor, registry.resolveConstructorInterceptors(constructor, resolved),
            additionalProxyConstructorParametersCount, parameters);
    }

    /**
     * Builds an independent constructor invocation from selected interceptors. Subclasses may return a
     * different implementation or decorate the default invocation while retaining its execution contract.
     * @param definition The bean definition
     * @param constructor The intercepted constructor
     * @param interceptors The selected interceptors, in invocation order
     * @param additionalProxyConstructorParametersCount The internal proxy constructor argument count
     * @param parameters The complete constructor arguments
     * @param <T> The bean type
     * @return A fresh constructor invocation
     * @since 5.3.0
     */
    protected <T> ConstructorInvocation<T> buildResolvedConstructorInvocation(
        BeanDefinition<T> definition, BeanConstructor<T> constructor, Interceptor<T, T>[] interceptors,
        int additionalProxyConstructorParametersCount, @Nullable Object... parameters) {
        return new ConstructorInterceptorChain<>(definition, constructor, interceptors,
            additionalProxyConstructorParametersCount, parameters);
    }
}
