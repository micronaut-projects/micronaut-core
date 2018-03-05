/*
 * Copyright 2017 original authors
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
package org.particleframework.aop.internal;

import org.particleframework.aop.*;
import org.particleframework.aop.exceptions.UnimplementedAdviceException;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Type;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.type.Argument;
import org.particleframework.core.type.MutableArgumentValue;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.annotation.DefaultAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link InvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorChain<B, R> implements InvocationContext<B,R> {
    protected final Interceptor<B, R>[] interceptors;
    protected final B target;
    protected final ExecutableMethod<B, R> executionHandle;
    protected final MutableConvertibleValues attributes = MutableConvertibleValues.of(new ConcurrentHashMap<>());
    protected final Map<String, MutableArgumentValue<?>> parameters = new LinkedHashMap<>();
    private final boolean isIntroduction;
    private int index = 0;

    public InterceptorChain(Interceptor<B, R>[] interceptors,
                            B target,
                            ExecutableMethod<B, R> method,
                            Object...originalParameters) {
        this.target = target;
        this.executionHandle = method;
        this.interceptors = new Interceptor[interceptors.length+1];
        System.arraycopy(interceptors, 0, this.interceptors, 0, interceptors.length);
        this.isIntroduction = target instanceof Introduced;
        if(isIntroduction) {
            this.interceptors[this.interceptors.length-1] = context -> {
                throw new UnimplementedAdviceException(method);
            };
        }
        else {
            this.interceptors[this.interceptors.length-1] = context -> method.invoke(target, getParameterValues());
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
        if(len == 0) {
            throw new IllegalStateException("At least one interceptor is required when calling proceed on the interceptor chain!");
        }
        int size = len - 1;
        if(index == size) {
            interceptor = this.interceptors[size];
        }
        else {
            interceptor = this.interceptors[index++];
        }
        return interceptor.intercept(this);
    }

    @Override
    public R proceed(Interceptor from) throws RuntimeException {
        for (int i = 0; i < interceptors.length; i++) {
            Interceptor<B, R> interceptor = interceptors[i];
            if(interceptor == from) {
                index = i + 1;
                return proceed();

            }
        }
        throw new IllegalArgumentException("Argument ["+from+"] is not within the interceptor chain");
    }



    /**
     * Resolves the {@link Around} interceptors for a method
     *
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @Internal
    public static Interceptor[] resolveAroundInterceptors(BeanContext beanContext, ExecutableMethod<?,?> method, Interceptor...interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        return resolveInterceptorsInternal(method, Around.class, interceptors);
    }

    /**
     * Resolves the interceptors for a method for {@link Introduction} advise. For {@link Introduction} advise
     * any {@link Around} advise interceptors are applied first
     *
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @Internal
    public static Interceptor[] resolveIntroductionInterceptors(BeanContext beanContext, ExecutableMethod<?, ?> method, Interceptor...interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(beanContext, method, interceptors);
        Interceptor[] introductionInterceptors = resolveInterceptorsInternal(method, Introduction.class, interceptors);
        if(introductionInterceptors.length == 0) {
            throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");
        }
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    private static void instrumentAnnotationMetadata(BeanContext beanContext, ExecutableMethod<?, ?> method) {
        if(beanContext instanceof ApplicationContext && method.getAnnotationMetadata() instanceof DefaultAnnotationMetadata) {
            // ensure metadata is environment aware
            ((DefaultAnnotationMetadata) method.getAnnotationMetadata()).configure(beanContext);
        }
    }


    private static Interceptor[] resolveInterceptorsInternal(ExecutableMethod<?,?> method, Class<? extends Annotation> annotationType, Interceptor[] interceptors) {
        Set<Class<? extends Annotation>> annotations = method.getAnnotationTypesByStereotype(annotationType);

        Set<Class> applicableClasses = annotations.stream()
                .filter(aClass -> {
                    if(annotationType == Around.class && aClass.getAnnotation(Introduction.class) != null) {
                        return false;
                    }
                    else if(annotationType == Introduction.class && aClass.getAnnotation(Around.class) != null) {
                        return false;
                    }
                    return true;
                })
                .map(type-> type.getAnnotation(Type.class))
                .filter(Objects::nonNull)
                .flatMap(type ->
                        Arrays.stream(type.value())
                ).collect(Collectors.toSet());

        Interceptor[] interceptorArray = Arrays.stream(interceptors)
                .filter(i -> applicableClasses.stream().anyMatch((t)->t.isInstance(i)))
                .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptorArray);
        return interceptorArray;
    }
}
