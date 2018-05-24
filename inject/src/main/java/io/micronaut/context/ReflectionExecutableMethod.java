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

package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A fallback implementation of {@link ExecutableMethod} that uses reflection in the case where no invocation data has
 * been produced at compile time (which should be rarely).
 *
 * @param <T> The type
 * @param <R> The result type
 * @author Graeme Rocher
 * @since 1.0
 */
class ReflectionExecutableMethod<T, R> implements ExecutableMethod<T, R> {

    private final BeanDefinition<T> beanDefinition;
    private final Method method;
    private final Argument[] arguments;

    /**
     * @param beanDefinition The bean definition
     * @param method         The method
     */
    ReflectionExecutableMethod(BeanDefinition<T> beanDefinition, Method method) {
        this.beanDefinition = beanDefinition;
        this.method = method;
        this.method.setAccessible(true);

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 0) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            List<Argument> arguments = new ArrayList<>(parameterTypes.length);

            for (int i = 0; i < parameterTypes.length; i++) {
                Class<? extends Annotation> qualifierType = AnnotationUtil.findAnnotationWithStereoType(Qualifier.class, parameterAnnotations[i])
                    .map(Annotation::annotationType).orElse(null);
                arguments.add(Argument.of(
                    method,
                    "arg" + i,
                    i,
                    qualifierType
                ));
            }
            this.arguments = arguments.toArray(new Argument[arguments.size()]);
        } else {
            this.arguments = Argument.ZERO_ARGUMENTS;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ReflectionExecutableMethod<?, ?> that = (ReflectionExecutableMethod<?, ?>) o;

        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    @Override
    public Class[] getArgumentTypes() {
        return method.getParameterTypes();
    }

    @Override
    public Method getTargetMethod() {
        return method;
    }

    @Override
    public ReturnType<R> getReturnType() {
        return new MethodReturnType<>();
    }

    @Override
    public Class getDeclaringType() {
        return beanDefinition.getBeanType();
    }

    @Override
    public String getMethodName() {
        return method.getName();
    }

    @Override
    public Argument[] getArguments() {
        return arguments;
    }

    @Override
    public R invoke(T instance, Object... arguments) {
        return ReflectionUtils.invokeMethod(instance, method, arguments);
    }

    @Override
    public String toString() {
        return method.toString();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return method.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return method.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return method.getDeclaredAnnotations();
    }

    /**
     * @param <MRT> The method return type
     */
    private class MethodReturnType<MRT> implements ReturnType<MRT> {

        @Override
        public Class<MRT> getType() {
            return (Class<MRT>) method.getReturnType();
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            return Collections.emptyMap();
        }

        @Override
        public AnnotatedElement[] getAnnotatedElements() {
            return new AnnotatedElement[]{method.getAnnotatedReturnType(), method};
        }
    }
}
