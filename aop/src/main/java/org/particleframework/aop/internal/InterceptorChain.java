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
import org.particleframework.context.annotation.Type;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.MutableConvertibleValues;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.core.type.MutableArgumentValue;

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
    private final boolean isIntroduction;
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
        this.interceptors = new Interceptor[interceptors.length+1];
        System.arraycopy(interceptors, 0, this.interceptors, 0, interceptors.length);
        this.isIntroduction = target instanceof Introduced;
        if(isIntroduction) {
            this.interceptors[this.interceptors.length-1] = context -> {
                throw new UnsupportedOperationException("Introduction advice reached the end of the chain and possible implementations were found");
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
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        return attributes.get(name, requiredType);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
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
        if(len == 0) {
            throw new IllegalStateException("At least one interceptor is required when calling proceed on the interceptor chain!");
        }
        boolean last = false;
        int size = len - 1;
        if(index == size) {
            last = true;
            interceptor = this.interceptors[size];
        }
        else {
            if(isIntroduction && index == size -1) {
                // the last interceptor in the chain for @Introduction advise throws UnsupportedException, so we consider it the last
                // this ensures cleanup before the exception is thrown
                last = true;
            }
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
        return findAnnotation(annotationClass).orElse(null);
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return executionHandle.getAnnotatedElements();
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
     * Resolves the {@link Around} interceptors for a method
     *
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @Internal
    public static Interceptor[] resolveAroundInterceptors(AnnotatedElement method, Interceptor...interceptors) {
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
    public static Interceptor[] resolveIntroductionInterceptors(AnnotatedElement method, Interceptor...interceptors) {
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(method, interceptors);
        Interceptor[] introductionInterceptors = resolveInterceptorsInternal(method, Introduction.class, interceptors);
        if(introductionInterceptors.length == 0) {
            throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");
        }
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }


    private static Interceptor[] resolveInterceptorsInternal(AnnotatedElement method, Class<? extends Annotation> annotationType, Interceptor[] interceptors) {
        Set<Annotation> annotations;
        if(method instanceof ExecutableMethod) {
            ExecutableMethod executableMethod = (ExecutableMethod) method;
            annotations = new HashSet<>(executableMethod.findAnnotationsWithStereoType(annotationType));
        }
        else {
            annotations = new HashSet<>(AnnotationUtil.findAnnotationsWithStereoType(annotationType, method.getAnnotations()));
        }

        Set<Class> applicableClasses = annotations.stream()
                .map((Annotation ann) -> ann.annotationType().getAnnotation(Type.class))
                .filter(Objects::nonNull)
                .flatMap(type ->
                        Arrays.stream(type.value())
                ).collect(Collectors.toSet());

        Interceptor[] interceptorArray = Arrays.stream(interceptors)
                .filter(i -> applicableClasses.contains(i.getClass()))
                .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptors);
        return interceptorArray;
    }
}
