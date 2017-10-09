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
package org.particleframework.context;

import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.core.type.ReturnType;
import org.particleframework.context.annotation.Executable;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * A fallback implementation of {@link ExecutableMethod} that uses reflection in the case where no invocation data has
 * been produced at compile time (which should be rarely)
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ReflectionExecutableMethod<T,R> implements ExecutableMethod<T,R> {

    private final BeanDefinition<T> beanDefinition;
    private final Method method;
    private final Argument[] arguments;

    ReflectionExecutableMethod(BeanDefinition<T> beanDefinition, Method method) {
        this.beanDefinition = beanDefinition;
        this.method = method;
        this.method.setAccessible(true);

        Class<?>[] parameterTypes = method.getParameterTypes();
        if(parameterTypes.length > 0) {

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
        }
        else {
            this.arguments =  Argument.ZERO_ARGUMENTS;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

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
    public Collection<? extends Annotation> getExecutableAnnotations() {
        return AnnotationUtil.findAnnotationsWithStereoType(Executable.class, method.getAnnotations());
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
        return beanDefinition.getType();
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

    private class MethodReturnType<MRT> implements ReturnType<MRT> {

        @Override
        public Class<MRT> getType() {
            return (Class<MRT>) method.getReturnType();
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            // TODO: build via reflection
            return Collections.emptyMap();
        }

        @Override
        public AnnotatedElement[] getAnnotatedElements() {
            return new AnnotatedElement[] { method.getAnnotatedReturnType(), method};
        }
    }
}
