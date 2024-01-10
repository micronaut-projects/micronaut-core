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

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 *
 * An internal representation of the {@link Interceptor} chain. This class implements {@link InvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <B> The declaring type
 * @param <R> The result of the method call
 *
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
     * @return The filtered array of interceptors
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static Interceptor[] resolveAroundInterceptors(
            @Nullable BeanContext beanContext,
            ExecutableMethod<?, ?> method,
            List<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        return resolveInterceptors(beanContext, method, interceptors, InterceptorKind.AROUND);
    }


    /**
     * Resolves the {@link Introduction} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static Interceptor[] resolveIntroductionInterceptors(
            @Nullable BeanContext beanContext,
            ExecutableMethod<?, ?> method,
            List<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        final Interceptor[] introductionInterceptors = resolveInterceptors(beanContext, method, interceptors, InterceptorKind.INTRODUCTION);
        final Interceptor[] aroundInterceptors = resolveInterceptors(beanContext, method, interceptors, InterceptorKind.AROUND);
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
                introductionInterceptors = new Interceptor[] { new AdapterIntroduction(beanContext, method) };
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        }
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(beanContext, method, interceptors);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @NonNull
    private static Interceptor[] resolveInterceptors(
            BeanContext beanContext,
            ExecutableMethod<?, ?> method,
            List<BeanRegistration<Interceptor<?, ?>>> interceptors,
            InterceptorKind interceptorKind) {
        return beanContext.getBean(InterceptorRegistry.class)
                    .resolveInterceptors(
                            (ExecutableMethod) method,
                            (List) interceptors,
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

    private static Interceptor[] resolveInterceptorsInternal(ExecutableMethod<?, ?> method, Class<? extends Annotation> annotationType, Interceptor[] interceptors, @NonNull ClassLoader classLoader) {
        List<Class<? extends Annotation>> annotations = method.getAnnotationTypesByStereotype(annotationType, classLoader);

        Set<Class<?>> applicableClasses = new HashSet<>();

        for (Class<? extends Annotation> aClass: annotations) {
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

        Interceptor[] interceptorArray = Arrays.stream(interceptors)
            .filter(i -> applicableClasses.stream().anyMatch(t -> t.isInstance(i)))
            .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptorArray);
        return interceptorArray;
    }
}
