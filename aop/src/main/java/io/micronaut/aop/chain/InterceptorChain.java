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

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Around;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link InvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <B> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorChain<B, R> extends AbstractInterceptorChain<B, R> implements InvocationContext<B, R> {

    protected final B target;
    protected final ExecutableMethod<B, R> executionHandle;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Constructor.
     *
     * @param interceptors array of interceptors
     * @param target target type
     * @param method result method
     * @param originalParameters parameters
     */
    public InterceptorChain(Interceptor<B, R>[] interceptors,
                            B target,
                            ExecutableMethod<B, R> method,
                            Object... originalParameters) {
        super(interceptors, originalParameters);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Intercepted method [{}] invocation on target: {}", method, target);
        }
        this.target = target;
        this.executionHandle = method;
        AnnotationMetadata metadata = executionHandle.getAnnotationMetadata();
        if (metadata instanceof EvaluatedAnnotationMetadata eam) {
            this.annotationMetadata = eam.withArguments(target, originalParameters);
        } else {
            this.annotationMetadata = metadata;
        }
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Argument[] getArguments() {
        return executionHandle.getArguments();
    }

    @Override
    public R invoke(B instance, Object... arguments) {
        return proceed();
    }

    @NonNull
    @Override
    public B getTarget() {
        return target;
    }

    @Override
    public R proceed() throws RuntimeException {
        Interceptor<B, R> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            try {
                return executionHandle.invoke(target, getParameterValues());
            } catch (AbstractMethodError e) {
                throw new UnimplementedAdviceException(executionHandle);
            }
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
            }

            return interceptor.intercept(this);
        }
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveAroundInterceptors(BeanContext beanContext,
                                                                    ExecutableMethod<T, ?> method,
                                                                    List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        return resolveInterceptors(beanContext, method, interceptors, InterceptorKind.AROUND);
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param interceptorRegistry the interceptor registry
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     * @since 4.3.0
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveAroundInterceptors(InterceptorRegistry interceptorRegistry,
                                                                    ExecutableMethod<T, ?> method,
                                                                    List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        return resolveInterceptors(interceptorRegistry, method, interceptors, InterceptorKind.AROUND);
    }

    /**
     * Resolves the {@link Introduction} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     * @since 4.3.0
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveIntroductionInterceptors(BeanContext beanContext,
                                                                          ExecutableMethod<T, ?> method,
                                                                          List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        final Interceptor<T, ?>[] introductionInterceptors = resolveInterceptors(beanContext, method, interceptors, InterceptorKind.INTRODUCTION);
        final Interceptor<T, ?>[] aroundInterceptors = resolveInterceptors(beanContext, method, interceptors, InterceptorKind.AROUND);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    /**
     * Resolves the {@link Introduction} interceptors for a method.
     *
     * @param interceptorRegistry the interceptor registry
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     * @since 4.3.0
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveIntroductionInterceptors(InterceptorRegistry interceptorRegistry,
                                                                          ExecutableMethod<T, ?> method,
                                                                          List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        final Interceptor<T, ?>[] introductionInterceptors = resolveInterceptors(interceptorRegistry, method, interceptors, InterceptorKind.INTRODUCTION);
        final Interceptor<T, ?>[] aroundInterceptors = resolveInterceptors(interceptorRegistry, method, interceptors, InterceptorKind.AROUND);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     * @deprecated Replaced by {@link #resolveAroundInterceptors(BeanContext, ExecutableMethod, List)}
     */
    // IMPLEMENTATION NOTE: This method is deprecated but should not be removed as it would break binary compatibility
    @SuppressWarnings({"WeakerAccess", "rawtypes"})
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    public static Interceptor[] resolveAroundInterceptors(@Nullable BeanContext beanContext, ExecutableMethod<?, ?> method, Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        return resolveInterceptorsInternal(method, Around.class, interceptors, beanContext != null ? beanContext.getClassLoader() : InterceptorChain.class.getClassLoader());
    }

    /**
     * Resolves the interceptors for a method for {@link Introduction} advise. For {@link Introduction} advise
     * any {@link Around} advise interceptors are applied first
     *
     * @param beanContext Bean Context
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    // IMPLEMENTATION NOTE: This method is deprecated but should not be removed as it would break binary compatibility
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    public static Interceptor[] resolveIntroductionInterceptors(@Nullable BeanContext beanContext,
                                                                ExecutableMethod<?, ?> method,
                                                                Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        Interceptor[] introductionInterceptors = resolveInterceptorsInternal(method, Introduction.class, interceptors, beanContext != null ? beanContext.getClassLoader() : InterceptorChain.class.getClassLoader());
        if (introductionInterceptors.length == 0) {
            if (method.hasStereotype(Adapter.class)) {
                introductionInterceptors = new Interceptor[] {new AdapterIntroduction(beanContext, method)};
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        }
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(beanContext, method, interceptors);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    @NonNull
    private static <T> Interceptor<T, ?>[] resolveInterceptors(BeanContext beanContext,
                                                               ExecutableMethod<T, ?> method,
                                                               List<BeanRegistration<Interceptor<T, ?>>> interceptors,
                                                               InterceptorKind interceptorKind) {
        return resolveInterceptors(beanContext.getBean(InterceptorRegistry.class), method, interceptors, interceptorKind);
    }

    @NonNull
    private static <T> Interceptor<T, ?>[] resolveInterceptors(InterceptorRegistry interceptorRegistry,
                                                               ExecutableMethod<T, ?> method,
                                                               List<BeanRegistration<Interceptor<T, ?>>> interceptors,
                                                               InterceptorKind interceptorKind) {
        return interceptorRegistry.resolveInterceptors(
            method,
            interceptors,
            interceptorKind
        );
    }

    private static void instrumentAnnotationMetadata(BeanContext beanContext, ExecutableMethod<?, ?> method) {
        if (beanContext instanceof ApplicationContext context && method instanceof EnvironmentConfigurable m) {
            if (m.hasPropertyExpressions()) {
                m.configure(context.getEnvironment());
            }
        }
    }

    private static <T> Interceptor<T, ?>[] resolveInterceptorsInternal(ExecutableMethod<?, ?> method,
                                                                       Class<? extends Annotation> annotationType,
                                                                       Interceptor<T, ?>[] interceptors,
                                                                       @NonNull ClassLoader classLoader) {
        List<Class<? extends Annotation>> annotations = method.getAnnotationTypesByStereotype(annotationType, classLoader);

        Set<Class<?>> applicableClasses = new HashSet<>();

        for (Class<? extends Annotation> aClass : annotations) {
            if (annotationType == Around.class && aClass.getAnnotation(Around.class) == null && aClass.getAnnotation(Introduction.class) != null) {
                continue;
            } else if (annotationType == Introduction.class && aClass.getAnnotation(Introduction.class) == null && aClass.getAnnotation(Around.class) != null) {
                continue;
            }
            Type typeAnn = aClass.getAnnotation(Type.class);
            if (typeAnn != null) {
                applicableClasses.addAll(Arrays.asList(typeAnn.value()));
            }
        }

        Interceptor<T, ?>[] interceptorArray = Arrays.stream(interceptors)
            .filter(i -> applicableClasses.stream().anyMatch(t -> t.isInstance(i)))
            .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptorArray);
        return interceptorArray;
    }
}
