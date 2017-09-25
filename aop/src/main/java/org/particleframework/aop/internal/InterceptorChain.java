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

import org.particleframework.aop.Around;
import org.particleframework.aop.Interceptor;
import org.particleframework.aop.InvocationContext;
import org.particleframework.context.annotation.Type;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.MutableConvertibleValues;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.MutableArgumentValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.*;
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
    protected final MutableConvertibleValues attributes;
    protected final Map<String, MutableArgumentValue<?>> parameters = new LinkedHashMap<>();
    private int index = 0;

    public InterceptorChain(Interceptor<B, R>[] interceptors,
                            B target,
                            ExecutableMethod<B, R> method,
                            Object...originalParameters) {
        this.target = target;
        this.executionHandle = method;
        this.attributes = AopAttributes.get(method.getDeclaringType(),
                                            method.getMethodName(),
                                            method.getArgumentTypes());
        OrderUtil.sort(interceptors);
        this.interceptors = new Interceptor[interceptors.length+1];
        System.arraycopy(interceptors, 0, this.interceptors, 0, interceptors.length);
        Argument[] arguments = method.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = method.getArguments()[i];
            Object value = originalParameters[i];
            parameters.put(argument.getName(), MutableArgumentValue.create(argument, value));
        }
        this.interceptors[this.interceptors.length-1] = context -> method.invoke(target, getParameterValues());
    }


    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return attributes.get(name, requiredType);
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
        return null;
    }

    @Override
    public R proceed() throws RuntimeException {
        Interceptor<B, R> interceptor;
        int len = this.interceptors.length;
        boolean last = false;
        int size = len - 1;
        if(index == size) {
            last = true;
            interceptor = this.interceptors[size];
        }
        else {

            interceptor = this.interceptors[index++];
        }
        try {
            return interceptor.intercept(this);
        } finally {
            if(last) {
                AopAttributes.remove(executionHandle.getDeclaringType(), executionHandle.getMethodName(), executionHandle.getArgumentTypes());
            }
        }
    }


    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return executionHandle.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return executionHandle.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return executionHandle.getDeclaredAnnotations();
    }

    @Override
    public InterceptorChain<B,R> put(CharSequence key, Object value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public Set<String> getNames() {
        return attributes.getNames();
    }

    @Override
    public InterceptorChain<B,R> remove(CharSequence key) {
        attributes.remove(key);
        return this;
    }

    @Override
    public InterceptorChain<B,R> clear() {
        attributes.clear();
        return this;
    }


    /**
     * Resolves the interceptors for a method
     *
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    public static Interceptor[] resolveInterceptors(AnnotatedElement method, Interceptor...interceptors) {
        Set<? extends Annotation> annotations = AnnotationUtil.findAnnotationsWithStereoType(Around.class, method.getAnnotations());
        Set<Class> applicableClasses = annotations.stream()
                .map((Annotation ann) -> ann.annotationType().getAnnotation(Type.class))
                .filter(Objects::nonNull)
                .flatMap(type ->
                        Arrays.stream(type.value())
                ).collect(Collectors.toSet());

        return Arrays.stream(interceptors)
                .filter(i -> applicableClasses.contains(i.getClass()))
                .toArray(Interceptor[]::new);
    }
}
