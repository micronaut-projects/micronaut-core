/*
 * Copyright 2018 original authors
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

import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A constructor injection point for the non-reflection case
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class DefaultConstructorInjectionPoint<T> implements ConstructorInjectionPoint<T> {

    private final BeanDefinition<T> declaringBean;
    private final Class<T> declaringType;
    private final Class[] argTypes;
    private final AnnotationMetadata annotationMetadata;
    private final Argument<?>[] arguments;

    DefaultConstructorInjectionPoint(
             BeanDefinition<T> declaringBean,
             Class<T> declaringType,
             AnnotationMetadata annotationMetadata,
             Argument<?>[] arguments) {
        this.argTypes = Argument.toClassArray(arguments);
        this.declaringBean = declaringBean;
        this.declaringType = declaringType;
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
        this.arguments = arguments == null ? Argument.ZERO_ARGUMENTS : arguments;
    }

    @Override
    public T invoke(Object... args) {
        Optional<Constructor<T>> potentialConstructor = ReflectionUtils.findConstructor(declaringType, argTypes);
        if(potentialConstructor.isPresent()) {
            return ReflectionConstructorInjectionPoint.invokeConstructor(potentialConstructor.get(), arguments, args);
        }
        throw new BeanInstantiationException("Constructor not found for type: " + this);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return declaringBean;
    }

    @Override
    public boolean requiresReflection() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultConstructorInjectionPoint<?> that = (DefaultConstructorInjectionPoint<?>) o;
        return Objects.equals(declaringType, that.declaringType) &&
                Arrays.equals(argTypes, that.argTypes);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(declaringType);
        result = 31 * result + Arrays.hashCode(argTypes);
        return result;
    }

    @Override
    public String toString() {
        return declaringType.getName() + "(" + Argument.toString(arguments) + ")";
    }
}
