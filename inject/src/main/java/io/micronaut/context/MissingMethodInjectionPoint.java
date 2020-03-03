/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.MethodInjectionPoint;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * A method injection point that represents a method that does not exist.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class MissingMethodInjectionPoint implements MethodInjectionPoint {

    private final BeanDefinition<?> definition;
    private final Class<?> declaringType;
    private final String methodName;
    private final Argument[] argTypes;

    /**
     * @param definition    The bean definition
     * @param declaringType The declaring class type
     * @param methodName    The method name
     * @param argTypes      The argument types
     */
    MissingMethodInjectionPoint(
        BeanDefinition<?> definition,
        Class<?> declaringType,
        String methodName,
        Argument[] argTypes) {

        this.definition = definition;
        this.declaringType = declaringType;
        this.methodName = methodName;
        this.argTypes = argTypes;
    }

    @Override
    public Method getMethod() {
        Class[] types = Arrays.stream(argTypes).map(Argument::getType).toArray(Class[]::new);
        throw ReflectionUtils.newNoSuchMethodError(declaringType, methodName, types);
    }

    @Override
    public String getName() {
        return methodName;
    }

    @Override
    public boolean isPreDestroyMethod() {
        return false;
    }

    @Override
    public boolean isPostConstructMethod() {
        return false;
    }

    @Override
    public Object invoke(Object instance, Object... args) {
        Class[] types = Arrays.stream(argTypes).map(Argument::getType).toArray(Class[]::new);
        throw ReflectionUtils.newNoSuchMethodError(declaringType, methodName, types);
    }

    @Override
    public Argument<?>[] getArguments() {
        return argTypes;
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return definition;
    }

    @Override
    public boolean requiresReflection() {
        return false;
    }
}
