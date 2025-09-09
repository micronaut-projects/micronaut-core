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
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

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
    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle, Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
        this.kind = null;
    }

    @Override
    @NonNull
    public InterceptorKind getKind() {
        return this.kind != null ? kind : target instanceof Introduced ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND;
    }

    @Override
    public R invoke(T instance, Object... arguments) {
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

            if (interceptor instanceof MethodInterceptor) {
                return ((MethodInterceptor<T, R>) interceptor).intercept(this);
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

    @NonNull
    @Override
    public Class<T> getDeclaringType() {
        return executionHandle.getDeclaringType();
    }

    @Override
    public String toString() {
        return executionHandle.toString();
    }

    @NonNull
    @Override
    public ExecutableMethod<T, R> getExecutableMethod() {
        return executionHandle;
    }

    /**
     * Internal method that handles the logic for executing {@link InterceptorKind#POST_CONSTRUCT} interception.
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param definition The definition
     * @param postConstructMethod The post construct method
     * @param bean The bean
     * @param <T1> The bean type
     * @return the bean instance
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    @NonNull
    public static <T1> T1 initialize(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull BeanContext beanContext,
        @NonNull BeanDefinition<T1> definition,
        @NonNull ExecutableMethod<T1, T1> postConstructMethod,
        @NonNull T1 bean) {
        return doIntercept(
            resolutionContext,
            beanContext,
            definition,
            postConstructMethod,
            bean,
            InterceptorKind.POST_CONSTRUCT
        );
    }

    /**
     * Internal method that handles the logic for executing {@link InterceptorKind#PRE_DESTROY} interception.
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param definition The definition
     * @param preDestroyMethod The pre destroy method
     * @param bean The bean
     * @param <T1> The bean type
     * @return the bean instance
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    @NonNull
    public static <T1> T1 dispose(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull BeanContext beanContext,
        @NonNull BeanDefinition<T1> definition,
        @NonNull ExecutableMethod<T1, T1> preDestroyMethod,
        @NonNull T1 bean) {
        return doIntercept(
            resolutionContext,
            beanContext,
            definition,
            preDestroyMethod,
            bean,
            InterceptorKind.PRE_DESTROY
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T1> T1 doIntercept(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> interceptedMethod,
        T1 bean,
        InterceptorKind kind) {
        final AnnotationMetadata annotationMetadata = interceptedMethod.getAnnotationMetadata();
        final Collection<AnnotationValue<?>> binding = resolveInterceptorValues(annotationMetadata, kind);

        final Collection<BeanRegistration<Interceptor<?, ?>>> resolved = resolutionContext.getBeanRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBindingValues(binding)
        );
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor[] resolvedInterceptors = interceptorRegistry
            .resolveInterceptors(
                (ExecutableMethod) interceptedMethod,
                (Collection) resolved,
                kind
            );

        if (ArrayUtils.isNotEmpty(resolvedInterceptors)) {
            final MethodInterceptorChain<T1, T1> chain = new MethodInterceptorChain<>(
                resolvedInterceptors,
                bean,
                interceptedMethod,
                kind
            );
            return Objects.requireNonNull(
                chain.proceed(),
                kind.name() + " interceptor chain illegal returned null for type: " + definition.getBeanType()
            );
        } else {
            return interceptedMethod.invoke(bean);
        }
    }
}
