/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.aop.Introduced;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

import static io.micronaut.core.util.ArrayUtils.EMPTY_OBJECT_ARRAY;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link LifecycleInvocation} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <T> type
 * @param <R> result
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@UsedByGeneratedCode
public final class MethodInterceptorChain<T, R> extends InterceptorChain<T, R> implements LifecycleInvocation<T, R> {

    private final @Nullable InterceptorKind kind;

    /**
     * Constructor for empty parameters.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     */
    @UsedByGeneratedCode
    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle) {
        this(interceptors, target, executionHandle, (InterceptorKind) null);
    }

    /**
     * Constructor for empty parameters.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     * @param kind The interception kind
     */
    public MethodInterceptorChain(
        Interceptor<T, R>[] interceptors,
        T target,
        ExecutableMethod<T, R> executionHandle,
        @Nullable InterceptorKind kind) {
        this(interceptors, target, executionHandle, kind, EMPTY_OBJECT_ARRAY);
    }

    /**
     * Creates an invocation with an explicit kind and arguments.
     * @param interceptors The selected interceptors
     * @param target The invocation target
     * @param executionHandle The executable method
     * @param kind The interception kind
     * @param originalParameters The invocation arguments
     * @since 5.3.0
     */
    public MethodInterceptorChain(
        Interceptor<T, R>[] interceptors,
        T target,
        ExecutableMethod<T, R> executionHandle,
        @Nullable InterceptorKind kind,
        @Nullable Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
        this.kind = kind;
    }

    /**
     * Constructor.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     * @param originalParameters originalParameters
     */
    @UsedByGeneratedCode
    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle, @Nullable Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
        this.kind = null;
    }

    @Override
    public InterceptorKind getKind() {
        return this.kind != null ? kind : target instanceof Introduced ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND;
    }

    @Override
    @Nullable
    public R invoke(T instance, @Nullable Object... arguments) {
        return new MethodInterceptorChain<>(interceptors, instance, executionHandle, originalParameters).proceed();
    }

    @Override
    public boolean isSuspend() {
        return executionHandle.isSuspend();
    }

    @Override
    public boolean isAbstract() {
        return executionHandle.isAbstract();
    }

    @Override
    @Nullable
    public R proceed() throws RuntimeException {
        Interceptor<T, R> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            if (target instanceof Introduced && executionHandle.isAbstract()) {
                throw new UnimplementedAdviceException(executionHandle);
            } else {
                return executionHandle.invoke(target, getParameterValues());
            }
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
            }

            if (interceptor instanceof MethodInterceptor<T, R> methodInterceptor) {
                return methodInterceptor.intercept(this);
            } else {
                return interceptor.intercept(this);
            }
        }
    }

    /**
     * Executes a lifecycle invocation, preserving nullable unadvised results and rejecting null advice results.
     * @return The invocation result
     * @since 5.3.0
     */
    @Override
    @Nullable
    public R proceedLifecycle() {
        if (interceptorCount == 0) {
            return executionHandle.invoke(target);
        }
        return Objects.requireNonNull(proceed(), getKind().name() + " interceptor chain illegal returned null for type: " + executionHandle.getDeclaringType());
    }

    @Override
    public String getMethodName() {
        return executionHandle.getMethodName();
    }

    @Override
    public Class<?>[] getArgumentTypes() {
        return executionHandle.getArgumentTypes();
    }

    @Override
    public Method getTargetMethod() {
        return executionHandle.getTargetMethod();
    }

    @Override
    public boolean hasTargetMethod() {
        return executionHandle.hasTargetMethod();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executionHandle.getReturnType();
    }

    @Override
    public Class<T> getDeclaringType() {
        return executionHandle.getDeclaringType();
    }

    @Override
    public String toString() {
        return executionHandle.toString();
    }

    @Override
    public ExecutableMethod<T, R> getExecutableMethod() {
        return executionHandle;
    }

    /**
     * Internal method that handles the logic for executing {@link InterceptorKind#POST_CONSTRUCT} interception.
     *
     * <p>Delegates to the chain factory, which reuses the candidates retained during creation.
     * Earlier generated definitions without retained candidates keep their binding-based resolution behavior.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param definition The definition
     * @param postConstructMethod The post construct method
     * @param bean The bean
     * @param <T1> The bean type
     * @return the bean instance
     * @since 3.0.0
     * @deprecated Bean definitions initialize through {@link InterceptorChainFactory#initialize}. Kept for definitions compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0")
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 initialize(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> postConstructMethod,
        T1 bean) {
        LegacyGeneratedCode.warn("MethodInterceptorChain.initialize");
        return initialize(resolutionContext, beanContext, definition, postConstructMethod, bean, null);
    }

    /**
     * Variant of {@link #initialize(BeanResolutionContext, BeanContext, BeanDefinition, ExecutableMethod, Object)}
     * that reuses registrations already resolved for this bean.
     *
     * <p>Called for a bean whose interceptors were resolved once while it was constructed, so that a
     * {@code @Prototype} interceptor which ran the constructor also runs {@code @PostConstruct}. Passing
     * {@code null} uses the candidates retained in the resolution context, falling back to binding-based
     * resolution for earlier generated definitions without retained candidates.</p>
     *
     * @param resolutionContext  The resolution context
     * @param beanContext        The bean context
     * @param definition         The definition
     * @param postConstructMethod The post construct method
     * @param bean               The bean
     * @param interceptors       Registrations resolved for this bean, or {@code null} to resolve them
     * @param <T1>               The bean type
     * @return the bean instance
     * @since 5.2.0
     * @deprecated Bean definitions initialize through {@link InterceptorChainFactory#initialize}. Kept for definitions compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0")
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 initialize(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> postConstructMethod,
        T1 bean,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        LegacyGeneratedCode.warn("MethodInterceptorChain.initialize");
        // Older generated callers use an empty explicit set to request discovery.
        return beanContext.getBean(InterceptorChainFactory.ARGUMENT).initialize(
            resolutionContext, definition, postConstructMethod, bean,
            interceptors == null || interceptors.isEmpty() ? null : interceptors
        );
    }

    /**
     * Internal method that handles the logic for executing {@link InterceptorKind#PRE_DESTROY} interception.
     *
     * <p>The chain factory reuses the candidates installed on the destruction context by the bean's
     * dependency owner. Earlier generated definitions without retained candidates keep their existing resolution
     * behavior.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param definition The definition
     * @param preDestroyMethod The pre destroy method
     * @param bean The bean
     * @param <T1> The bean type
     * @return the bean instance
     * @since 3.0.0
     * @deprecated Bean definitions dispose through {@link InterceptorChainFactory#dispose}. Kept for definitions compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0")
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 dispose(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> preDestroyMethod,
        T1 bean) {
        LegacyGeneratedCode.warn("MethodInterceptorChain.dispose");
        return dispose(resolutionContext, beanContext, definition, preDestroyMethod, bean, null);
    }

    /**
     * Variant of {@link #dispose(BeanResolutionContext, BeanContext, BeanDefinition, ExecutableMethod, Object)} that
     * reuses registrations already resolved for this bean.
     *
     * <p>Non-empty explicit registrations take precedence over candidates retained on the destruction context.
     * Null or empty explicit registrations preserve legacy discovery behavior; an empty retained set is authoritative.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param definition        The definition
     * @param preDestroyMethod  The pre destroy method
     * @param bean              The bean
     * @param interceptors      Registrations resolved for this bean, or {@code null} to resolve them
     * @param <T1>              The bean type
     * @return the bean instance
     * @since 5.2.0
     * @deprecated Bean definitions dispose through {@link InterceptorChainFactory#dispose}. Kept for definitions compiled by earlier versions.
     */
    @Deprecated(since = "5.3.0")
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 dispose(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> preDestroyMethod,
        T1 bean,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        LegacyGeneratedCode.warn("MethodInterceptorChain.dispose");
        // Older generated callers use an empty explicit set to request discovery.
        return beanContext.getBean(InterceptorChainFactory.ARGUMENT).dispose(
            resolutionContext, definition, preDestroyMethod, bean,
            interceptors == null || interceptors.isEmpty() ? null : interceptors
        );
    }
}
