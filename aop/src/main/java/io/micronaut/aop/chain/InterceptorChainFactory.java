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
import io.micronaut.aop.Introduced;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Context-local strategy for building and executing method and constructor interceptor chains.
 * Implementations can customize chain construction while retaining the default lifecycle and constructor
 * execution contracts. Implementations supply {@link #buildResolvedInvocation}, the common method/lifecycle
 * construction hook used by generated proxies. Candidate-based conveniences live on
 * {@link DefaultInterceptorChainFactory}. Each build returns independent invocation state.
 *
 * @since 5.3.0
 */
@Internal
@NullMarked
public interface InterceptorChainFactory {
    /** Constant for bean lookup. */
    Argument<InterceptorChainFactory> ARGUMENT = Argument.of(InterceptorChainFactory.class);

    /**
     * Builds a lifecycle chain. A non-null candidate set, including an empty one, takes precedence over
     * retained candidates. Null uses retained candidates, or compatibility discovery when none exist.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle interception kind
     * @param candidates Explicit candidates, or null to use retained candidates or discovery
     * @param <T> The bean type
     * @param <R> The result type
     * @return A new lifecycle chain; execute with {@link LifecycleInvocation#proceedLifecycle(BeanDefinition)}
     */
    <T, R> LifecycleInvocation<T, R> buildLifecycleChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        ExecutableMethod<T, R> method,
        T bean,
        InterceptorKind kind,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> candidates);

    /**
     * Builds a method invocation from interceptors already selected for its target. Generated proxies retain the
     * context's factory and their selected arrays; this operation does not resolve beans or repeat matching.
     *
     * @param bean The invocation target
     * @param method The intercepted method
     * @param interceptors The selected interceptors, in invocation order
     * @param parameters The invocation arguments
     * @param <T> The target type
     * @param <R> The result type
     * @return A fresh invocation with independent chain state
     */
    @UsedByGeneratedCode
    default <T, R> MethodInvocationContext<T, R> buildResolvedMethodChain(
        T bean,
        ExecutableMethod<T, R> method,
        Interceptor<T, R>[] interceptors,
        @Nullable Object... parameters) {
        return buildResolvedInvocation(bean, method, interceptors,
            bean instanceof Introduced ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND, parameters);
    }

    /**
     * Builds an invocation after selection. The default factory routes method and lifecycle construction through
     * this required hook, preserving the interception kind without repeating matching. Implementations must
     * supply it explicitly so generated calls cannot silently bypass their construction strategy.
     * Each call returns independent state.
     *
     * @param bean The target
     * @param method The intercepted method
     * @param interceptors The selected interceptors
     * @param kind The interception kind
     * @param parameters The invocation arguments
     * @param <T> The target type
     * @param <R> The result type
     * @return A fresh method or lifecycle invocation
     */
    <T, R> LifecycleInvocation<T, R> buildResolvedInvocation(
        T bean, ExecutableMethod<T, R> method, Interceptor<T, R>[] interceptors,
        InterceptorKind kind, @Nullable Object... parameters);

    /**
     * Builds a constructor chain. Null candidates permit discovery; an empty set does not.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param constructor The intercepted constructor
     * @param candidates Explicit candidates, or null to resolve them
     * @param additionalProxyConstructorParametersCount The internal proxy constructor argument count
     * @param parameters The complete constructor arguments
     * @param <T> The bean type
     * @return A new constructor chain; execute with {@link ConstructorInvocation#instantiate()}
     */
    <T> ConstructorInvocation<T> buildConstructorChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition,
        BeanConstructor<T> constructor,
        @Nullable Collection<BeanRegistration<Interceptor<T, T>>> candidates,
        int additionalProxyConstructorParametersCount,
        @Nullable Object... parameters);

    /**
     * Executes post-construct advice, enforcing its non-null result contract when advice matches.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param candidates Explicit candidates, or null to use retained candidates or discovery
     * @param <T> The bean type
     * @return The initialization result
     */
    default <T> @Nullable T initialize(BeanResolutionContext resolutionContext, BeanDefinition<T> definition,
                                      ExecutableMethod<T, T> method, T bean,
                                      @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> candidates) {
        return buildLifecycleChain(resolutionContext, definition, method, bean, InterceptorKind.POST_CONSTRUCT, candidates)
            .proceedLifecycle(definition);
    }

    /**
     * Executes pre-destroy advice, enforcing its non-null result contract when advice matches.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param candidates Explicit candidates, or null to use retained candidates or discovery
     * @param <T> The bean type
     * @return The disposal result
     */
    default <T> @Nullable T dispose(BeanResolutionContext resolutionContext, BeanDefinition<T> definition,
                                   ExecutableMethod<T, T> method, T bean,
                                   @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> candidates) {
        return buildLifecycleChain(resolutionContext, definition, method, bean, InterceptorKind.PRE_DESTROY, candidates)
            .proceedLifecycle(definition);
    }

    /**
     * Executes construction advice, distinguishing advice failures from constructor-body failures and
     * enforcing the non-null result contract.
     *
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param constructor The intercepted constructor
     * @param candidates Explicit candidates, or null to resolve them
     * @param additionalProxyConstructorParametersCount The internal proxy constructor argument count
     * @param parameters The complete constructor arguments
     * @param <T> The bean type
     * @return The constructed bean
     */
    default <T> T instantiate(BeanResolutionContext resolutionContext, BeanDefinition<T> definition,
                              BeanConstructor<T> constructor,
                              @Nullable Collection<BeanRegistration<Interceptor<T, T>>> candidates,
                              int additionalProxyConstructorParametersCount, @Nullable Object... parameters) {
        return buildConstructorChain(resolutionContext, definition, constructor, candidates,
            additionalProxyConstructorParametersCount, parameters).instantiate();
    }
}
