/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.reflection;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * A {@link BeanConstructor} over a {@link Constructor}, with the arguments and the annotation metadata a
 * generated constructor would have. A non-static inner class is instantiated as
 * {@link Constructor#newInstance(Object...)} instantiates it: the enclosing instance is the first argument.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionBeanConstructor<T> implements BeanConstructor<T> {

    private final Constructor<T> constructor;
    private final AnnotationMetadata annotationMetadata;
    private final Argument<?>[] arguments;

    /**
     * Creates a bean constructor over a constructor.
     *
     * @param constructor The constructor
     */
    public ReflectionBeanConstructor(Constructor<T> constructor) {
        this.constructor = constructor;
        constructor.trySetAccessible();
        this.annotationMetadata = ReflectionAnnotations.metadataOf(constructor);
        this.arguments = ReflectionArguments.argumentsOf(constructor);
    }

    /**
     * The bean constructor of a constructor.
     *
     * @param constructor The constructor
     * @param <T>         The bean type
     * @return The bean constructor
     */
    public static <T> ReflectionBeanConstructor<T> of(Constructor<T> constructor) {
        return new ReflectionBeanConstructor<>(constructor);
    }

    /**
     * The constructor this bean constructor instantiates through.
     *
     * @return The constructor
     */
    public Constructor<T> getConstructor() {
        return constructor;
    }

    @Override
    public Class<T> getDeclaringBeanType() {
        return constructor.getDeclaringClass();
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public T instantiate(@Nullable Object... parameterValues) {
        Object[] values = parameterValues == null ? new Object[0] : parameterValues;
        if (values.length != arguments.length) {
            throw new InstantiationException("The constructor " + getDescription() + " expects "
                + arguments.length + " argument(s), " + values.length + " given");
        }
        try {
            return constructor.newInstance(values);
        } catch (InvocationTargetException e) {
            throw new InstantiationException("Cannot instantiate " + constructor.getDeclaringClass().getName() + ": " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new InstantiationException("Cannot instantiate " + constructor.getDeclaringClass().getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ReflectionBeanConstructor<?> other && constructor.equals(other.constructor);
    }

    @Override
    public int hashCode() {
        return constructor.hashCode();
    }

    @Override
    public String toString() {
        return getDeclaringBeanType().getSimpleName() + "(" + Arrays.toString(constructor.getParameterTypes()) + ")";
    }
}
