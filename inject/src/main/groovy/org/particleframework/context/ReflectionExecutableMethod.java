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

import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.inject.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Map<String, Class> arguments = new LinkedHashMap<>();
        Map<String, Annotation> qualifiers = new LinkedHashMap<>();
        Map<String, List<Class>> genericTypes = new LinkedHashMap<>();

        buildReflectionMetadata(method, arguments, genericTypes);
        this.arguments = DefaultArgument.from(arguments, qualifiers, genericTypes, index -> {
            if(index < parameterAnnotations.length) {
                return parameterAnnotations[index];
            }
            return new Annotation[0];
        });
    }

    @Override
    public Class[] getArgumentTypes() {
        return method.getParameterTypes();
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

    private void buildReflectionMetadata(Method method, Map<String, Class> arguments, Map<String, List<Class>> genericTypes) {
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        int genTypesLength = genericParameterTypes != null ? genericParameterTypes.length : 0;
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            String paramName = "arg" + i;
            arguments.put(paramName, parameterType);
            if(i < genTypesLength) {
                Type genericType = genericParameterTypes[i];
                Class[] typeArguments = GenericTypeUtils.resolveTypeArguments(genericType);
                if(typeArguments != null) {
                    genericTypes.put(paramName, Arrays.asList(typeArguments));
                }
            }
        }
    }
}
