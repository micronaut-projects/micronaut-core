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
package io.micronaut.aop;

import io.micronaut.aop.chain.InterceptorCandidateResolver;
import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Strategy interface for looking up interceptors from the bean context.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@NullMarked
public interface InterceptorRegistry {
    /**
     * Constant for bean lookup.
     */
    Argument<InterceptorRegistry> ARGUMENT = Argument.of(InterceptorRegistry.class);

    /**
     * Resolves method interceptors for the given method.
     *
     * @param method The method interceptors
     * @param interceptors The pre-resolved interceptors
     * @param interceptorKind The interceptor kind
     * @param <T> the bean type
     * @return An array of interceptors
     */
    <T> Interceptor<T, ?>[] resolveInterceptors(
        Executable<T, ?> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> interceptors,
        InterceptorKind interceptorKind
    );

    /**
     * Selects the interceptors for a method invocation. Introduction invocations execute around advice
     * before introduction advice. Selection can be retained and reused by independent invocations.
     *
     * @param method The intercepted method
     * @param candidates The acquired interceptor registrations
     * @param kind The interception kind
     * @param <T> The bean type
     * @return The selected interceptors in invocation order
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    default <T> Interceptor<T, ?>[] resolveMethodInterceptors(
        ExecutableMethod<T, ?> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> candidates,
        InterceptorKind kind
    ) {
        Interceptor<T, ?>[] selected = resolveInterceptors(method, candidates, kind);
        if (kind != InterceptorKind.INTRODUCTION) {
            return selected;
        }
        return ArrayUtils.concat(resolveInterceptors(method, candidates, InterceptorKind.AROUND), selected);
    }

    /**
     * Acquires the candidates shared by a bean's construction and lifecycle phases.
     * @param resolutionContext The creation context
     * @param constructor The constructor with combined bean and constructor metadata
     * @param <T> The bean type
     * @return The candidates, or null when the bean declares no binding
     * @since 5.3.0
     */
    @Internal
    default <T> @Nullable List<BeanRegistration<Interceptor<T, T>>> resolveBeanCandidates(
        BeanResolutionContext resolutionContext, AnnotationMetadataProvider constructor) {
        return new InterceptorCandidateResolver(this).resolveBeanCandidates(resolutionContext, constructor);
    }

    /**
     * Retains lifecycle candidates before injection and initialization, reusing construction or proxy candidates.
     * @param resolutionContext The creation context
     * @param definition The bean definition
     * @param bean The constructed instance
     * @param initialization Whether post-construct interception needs its own candidates
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    default void captureLifecycleCandidates(BeanResolutionContext resolutionContext, BeanDefinition<?> definition,
                                            @Nullable Object bean, boolean initialization) {
        new InterceptorCandidateResolver(this).captureLifecycleCandidates(resolutionContext, definition, bean, initialization);
    }

    /**
     * Acquires candidates for construction when the caller supplies no explicit set.
     * @param resolutionContext The resolution context
     * @param definition The bean definition
     * @param constructor The intercepted constructor
     * @param <T> The bean type
     * @return The candidates
     * @since 5.3.0
     */
    @Internal
    default <T> Collection<BeanRegistration<Interceptor<T, T>>> resolveConstructorCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<T> definition, BeanConstructor<T> constructor) {
        return new InterceptorCandidateResolver(this).resolveConstructorCandidates(resolutionContext, definition, constructor);
    }

    /**
     * Returns retained lifecycle candidates, using legacy discovery only when none were recorded.
     * An explicitly retained empty set prevents discovery.
     * @param resolutionContext The resolution context
     * @param definition The lifecycle owner
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The candidates
     * @since 5.3.0
     */
    @Internal
    default Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<?> definition,
        ExecutableMethod<?, ?> method, Object bean, InterceptorKind kind) {
        return new InterceptorCandidateResolver(this).resolveLifecycleCandidates(resolutionContext, definition, method, bean, kind);
    }

    /**
     * Acquires and retains candidates for a runtime proxy, including constructor and lifecycle bindings.
     * @param resolutionContext The creation context
     * @param definition The proxy definition
     * @param methods The intercepted methods
     * @param <T> The bean type
     * @return The acquired registrations
     * @since 5.3.0
     */
    @Internal
    default <T> List<BeanRegistration<Interceptor<T, ?>>> resolveCandidates(
        BeanResolutionContext resolutionContext, BeanDefinition<T> definition,
        Collection<ExecutableMethod<T, ?>> methods) {
        return new InterceptorCandidateResolver(this).resolveCandidates(resolutionContext, definition, methods);
    }

    /**
     * Finds an existing registration for a target supplied to a proxy, including a swapped-in target.
     * This lookup neither creates a bean nor takes ownership of it.
     * @param beanLocator The context used by the proxy
     * @param bean The target, or null
     * @return The existing registration, or null when the locator does not hold it
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    default @Nullable BeanRegistration<?> findProxyTargetRegistration(BeanLocator beanLocator, @Nullable Object bean) {
        if (bean == null || !(beanLocator instanceof BeanDefinitionRegistry definitions)) {
            return null;
        }
        return definitions.findBeanRegistration(bean).orElse(null);
    }

    /**
     * Reuses or acquires the selection owned by one proxy target. Shared registrations keep their scope ownership.
     * @param beanLocator The context used for unmanaged targets
     * @param targetDefinition The target definition
     * @param methods The intercepted methods
     * @param introduction Whether introduction advice is required
     * @param target The target registration, if known
     * @param bean The invocation target
     * @return The interceptors selected for each method
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    default Interceptor<?, ?>[][] resolveTargetInterceptors(BeanLocator beanLocator,
        BeanDefinition<?> targetDefinition, ExecutableMethod<?, ?>[] methods, boolean introduction,
        @Nullable BeanRegistration<?> target, @Nullable Object bean) {
        return new InterceptorCandidateResolver(this)
            .resolveTargetInterceptors(beanLocator, targetDefinition, methods, introduction, target, bean);
    }

    /**
     * Resolves interceptors for the given constructor.
     *
     * @param constructor The constructor
     * @param interceptors The pre-resolved interceptors
     * @param <T> The bean type
     * @return An array of interceptors
     */
    <T> Interceptor<T, T>[] resolveConstructorInterceptors(
        BeanConstructor<T> constructor,
        Collection<BeanRegistration<Interceptor<T, T>>> interceptors
    );
}
