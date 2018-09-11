/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.aop.chain;

import io.micronaut.aop.*;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ExecutableMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
public class InterceptorChain<B, R> implements InvocationContext<B, R> {
    private static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);

    protected final Interceptor<B, R>[] interceptors;
    protected final B target;
    protected final ExecutableMethod<B, R> executionHandle;
    protected final MutableConvertibleValues attributes = MutableConvertibleValues.of(new ConcurrentHashMap<>());
    protected final Map<String, MutableArgumentValue<?>> parameters = new LinkedHashMap<>();

    private int index = 0;


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
        if (LOG.isTraceEnabled()) {
            LOG.trace("Intercepted method [{}] invocation on target: {}", method, target);
        }
        this.target = target;
        this.executionHandle = method;
        this.interceptors = new Interceptor[interceptors.length + 1];
        System.arraycopy(interceptors, 0, this.interceptors, 0, interceptors.length);
        boolean isIntroduction = target instanceof Introduced;
        if (isIntroduction) {
            this.interceptors[this.interceptors.length - 1] = context -> {
                throw new UnimplementedAdviceException(method);
            };
        } else {
            this.interceptors[this.interceptors.length - 1] = context -> method.invoke(target, getParameterValues());
        }
        Argument[] arguments = method.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = method.getArguments()[i];
            Object value = originalParameters[i];
            parameters.put(argument.getName(), MutableArgumentValue.create(argument, value));
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return executionHandle.getAnnotationMetadata();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public Argument[] getArguments() {
        return executionHandle.getArguments();
    }

    @Override
    public Map<String, MutableArgumentValue<?>> getParameters() {
        return parameters;
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
        int len = this.interceptors.length;
        if (len == 0) {
            throw new IllegalStateException("At least one interceptor is required when calling proceed on the interceptor chain!");
        }
        int size = len - 1;
        if (index == size) {
            interceptor = this.interceptors[size];
        } else {
            interceptor = this.interceptors[index++];
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
        }

        return interceptor.intercept(this);
    }

    @Override
    public R proceed(Interceptor from) throws RuntimeException {
        for (int i = 0; i < interceptors.length; i++) {
            Interceptor<B, R> interceptor = interceptors[i];
            if (interceptor == from) {
                index = i + 1;
                return proceed();

            }
        }
        throw new IllegalArgumentException("Argument [" + from + "] is not within the interceptor chain");
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @Internal
    public static Interceptor[] resolveAroundInterceptors(BeanContext beanContext, ExecutableMethod<?, ?> method, Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        return resolveInterceptorsInternal(method, Around.class, interceptors);
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
    @Internal
    public static Interceptor[] resolveIntroductionInterceptors(BeanContext beanContext, ExecutableMethod<?, ?> method, Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(beanContext, method, interceptors);
        Interceptor[] introductionInterceptors = resolveInterceptorsInternal(method, Introduction.class, interceptors);
        if (introductionInterceptors.length == 0) {
            if (method.hasStereotype(Adapter.class)) {
                introductionInterceptors = new Interceptor[] { new AdapterIntroduction(beanContext, method) };
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        }
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    private static void instrumentAnnotationMetadata(BeanContext beanContext, ExecutableMethod<?, ?> method) {
        if (beanContext instanceof ApplicationContext && method instanceof EnvironmentConfigurable) {
            // ensure metadata is environment aware
            ((EnvironmentConfigurable) method).configure(((ApplicationContext) beanContext).getEnvironment());
        }
    }

    private static Interceptor[] resolveInterceptorsInternal(ExecutableMethod<?, ?> method, Class<? extends Annotation> annotationType, Interceptor[] interceptors) {
        List<Class<? extends Annotation>> annotations = method.getAnnotationTypesByStereotype(annotationType);

        Set<Class> applicableClasses = annotations.stream()
            .filter(aClass -> {
                if (annotationType == Around.class && aClass.getAnnotation(Introduction.class) != null) {
                    return false;
                } else if (annotationType == Introduction.class && aClass.getAnnotation(Around.class) != null) {
                    return false;
                }
                return true;
            })
            .map(type -> type.getAnnotation(Type.class))
            .filter(Objects::nonNull)
            .flatMap(type ->
                Arrays.stream(type.value())
            ).collect(Collectors.toSet());

        Interceptor[] interceptorArray = Arrays.stream(interceptors)
            .filter(i -> applicableClasses.stream().anyMatch((t) -> t.isInstance(i)))
            .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptorArray);
        return interceptorArray;
    }
}
