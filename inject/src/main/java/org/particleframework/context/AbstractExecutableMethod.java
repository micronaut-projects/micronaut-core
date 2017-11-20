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

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.core.type.ReturnType;
import org.particleframework.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>Abstract base class for generated {@link ExecutableMethod} classes to implement. The generated classes should implement
 * the {@link ExecutableMethod#invoke(Object, Object...)} method at compile time providing direct dispatch of the target method</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractExecutableMethod implements ExecutableMethod {

    private final Argument[] arguments;
    private final Class declaringType;
    private final Annotation[] annotations;
    private final ReturnType returnType;
    private final Method method;

    protected AbstractExecutableMethod(Method method,
                                       Argument<?>[] genericReturnTypes,
                                       Argument...arguments) {
        this.method = method;
        this.returnType = new ReturnTypeImpl(method, genericReturnTypes);
        this.annotations = method.getAnnotations();
        this.declaringType = method.getDeclaringClass();
        this.arguments = arguments == null || arguments.length == 0 ? Argument.ZERO_ARGUMENTS : arguments;
    }

    protected AbstractExecutableMethod(Method method, Argument<?>[] genericReturnTypes) {
        this(method, genericReturnTypes, Argument.ZERO_ARGUMENTS);
    }

    @Override
    public Method getTargetMethod() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractExecutableMethod that = (AbstractExecutableMethod) o;

        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    @Override
    public String toString() {
        Stream<String> stringStream = Arrays.stream(getArguments()).map(Argument::toString);
        String text = stringStream.collect(Collectors.joining(","));
        return getReturnType().getType().getSimpleName() + " " + getMethodName() + "(" + text + ")";
    }

    @Override
    public ReturnType getReturnType() {
        return returnType;
    }

    @Override
    public Class[] getArgumentTypes() {
        return Arrays.stream(arguments)
                .map(Argument::getType)
                .toArray(Class[]::new);
    }

    @Override
    public Class getDeclaringType() {
        return declaringType;
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
    public final Object invoke(Object instance, Object... arguments) {
        validateArguments(arguments);
        return invokeInternal(instance, arguments);
    }

    private void validateArguments(Object[] argArray) {
        int requiredCount = this.arguments.length;
        int actualCount = argArray == null ? 0 : argArray.length;
        if(requiredCount != actualCount) {
            throw new IllegalArgumentException("Wrong number of arguments to method: " + method.getName());
        }
        if(requiredCount > 0) {
            for (int i = 0; i < arguments.length; i++) {
                Argument argument = arguments[i];
                Class type = ReflectionUtils.getWrapperType(argument.getType());
                Object value = argArray[i];
                if(value != null && !type.isInstance(value)) {
                    throw new IllegalArgumentException("Invalid type ["+argArray[i].getClass().getName()+"] for argument ["+argument+"] of method: " + method.getName());
                }
            }
        }
    }

    protected abstract Object invokeInternal(Object instance, Object[] arguments);

    class ReturnTypeImpl implements ReturnType<Object> {
        private final Method method;
        private final Map<String, Argument<?>> typeVariables;

        public ReturnTypeImpl(Method method, Argument<?>[] genericReturnType) {
            this.method = method;
            if(genericReturnType == null || genericReturnType.length == 0) {
                typeVariables = Collections.emptyMap();
            }
            else {
                typeVariables = new LinkedHashMap<>(genericReturnType.length);
                for (Argument<?> argument : genericReturnType) {
                    typeVariables.put(argument.getName(), argument);
                }
            }
        }

        @Override
        public Class<Object> getType() {
            return (Class<Object>) method.getReturnType();
        }


        @Override
        public AnnotatedElement[] getAnnotatedElements() {
            return new AnnotatedElement[] { method.getAnnotatedReturnType(), method};
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            return typeVariables;
        }
    }
}
