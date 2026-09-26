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
import io.micronaut.aop.MethodInvocationContext;
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

import static io.micronaut.core.util.ArrayUtils.EMPTY_OBJECT_ARRAY;
/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link MethodInvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <T> type
 * @param <R> result
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@UsedByGeneratedCode
public final class MethodInterceptorChain<T, R> extends InterceptorChain<T, R> implements MethodInvocationContext<T, R> {

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
        super(interceptors, target, executionHandle, EMPTY_OBJECT_ARRAY);
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
     * Runs the {@link InterceptorKind#POST_CONSTRUCT} interception of a bean.
     *
     * @param resolutionContext   The resolution context
     * @param beanContext         The bean context
     * @param definition          The definition
     * @param postConstructMethod The post construct method
     * @param bean                The bean
     * @param <T1>                The bean type
     * @return the bean instance
     * @since 3.0.0
     * @deprecated Since 5.3.0 the lifecycle events of a bean are intercepted by {@link LifecycleInterception}; this
     * chain only runs. Kept for bean definitions compiled by earlier versions, which call it directly.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 initialize(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> postConstructMethod,
        T1 bean) {
        return LifecycleInterception.initialize(resolutionContext, beanContext, definition, postConstructMethod, bean);
    }

    /**
     * Runs the {@link InterceptorKind#POST_CONSTRUCT} interception of a bean with the handed registrations.
     *
     * @param resolutionContext   The resolution context
     * @param beanContext         The bean context
     * @param definition          The definition
     * @param postConstructMethod The post construct method
     * @param bean                The bean
     * @param interceptors        Registrations resolved by the caller, used when not empty
     * @param <T1>                The bean type
     * @return the bean instance
     * @since 5.2.0
     * @deprecated Since 5.3.0 nothing in the framework hands registrations over, the interceptors of a bean being
     * resolved as the bean's own by {@link LifecycleInterception}. Kept, and honoured, for a caller compiled against 5.2.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    @Internal
    @Nullable
    public static <T1> T1 initialize(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> postConstructMethod,
        T1 bean,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        return LifecycleInterception.initialize(resolutionContext, beanContext, definition, postConstructMethod, bean, interceptors);
    }

    /**
     * Runs the {@link InterceptorKind#PRE_DESTROY} interception of a bean.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param definition        The definition
     * @param preDestroyMethod  The pre destroy method
     * @param bean              The bean
     * @param <T1>              The bean type
     * @return the bean instance
     * @since 3.0.0
     * @deprecated Since 5.3.0 the lifecycle events of a bean are intercepted by {@link LifecycleInterception}; this
     * chain only runs. Kept for bean definitions compiled by earlier versions, which call it directly.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 dispose(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> preDestroyMethod,
        T1 bean) {
        return LifecycleInterception.dispose(resolutionContext, beanContext, definition, preDestroyMethod, bean);
    }

    /**
     * Runs the {@link InterceptorKind#PRE_DESTROY} interception of a bean with the handed registrations.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param definition        The definition
     * @param preDestroyMethod  The pre destroy method
     * @param bean              The bean
     * @param interceptors      Registrations resolved by the caller, used when not empty
     * @param <T1>              The bean type
     * @return the bean instance
     * @since 5.2.0
     * @deprecated Since 5.3.0 nothing in the framework hands registrations over, the interceptors of a bean being
     * resolved as the bean's own by {@link LifecycleInterception}. Kept, and honoured, for a caller compiled against 5.2.
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    @Internal
    @Nullable
    public static <T1> T1 dispose(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> preDestroyMethod,
        T1 bean,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        return LifecycleInterception.dispose(resolutionContext, beanContext, definition, preDestroyMethod, bean, interceptors);
    }
}
