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

import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;

import java.lang.reflect.Constructor;

/**
 * An injection point for a constructor.
 *
 * @param <T> The constructor type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ReflectionConstructorInjectionPoint<T> implements ConstructorInjectionPoint<T> {

    private final Class<T> declaringType;
    private final Argument[] arguments;
    private final BeanDefinition declaringComponent;
    private final AnnotationMetadata annotationMetadata;
    private Constructor<T> constructor;

    /**
     * @param beanDefinition     The bean definition
     * @param declaringType      The declaring type
     * @param annotationMetadata The annotation metadata
     * @param arguments          The arguments to the constructor
     */
    ReflectionConstructorInjectionPoint(
        BeanDefinition beanDefinition,
        Class<T> declaringType,
        AnnotationMetadata annotationMetadata,
        Argument... arguments) {

        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
        this.declaringComponent = beanDefinition;
        this.declaringType = declaringType;
        this.arguments = arguments == null ? Argument.ZERO_ARGUMENTS : arguments;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public BeanDefinition getDeclaringBean() {
        return this.declaringComponent;
    }

    @Override
    public boolean requiresReflection() {
        return true;
    }

    @Override
    public Argument[] getArguments() {
        return arguments;
    }

    @Override
    public T invoke(Object... args) {
        return invokeConstructor(resolveConstructor(), getArguments(), args);
    }

    private Constructor<T> resolveConstructor() {
        Constructor<T> constructor = this.constructor;
        if (constructor == null) {
            synchronized (this) { // double check
                constructor = this.constructor;
                if (constructor == null) {
                    constructor = ReflectionUtils.findConstructor(declaringType, Argument.toClassArray(arguments))
                        .orElseThrow(() ->
                            new BeanInstantiationException(
                                declaringComponent,
                                "No constructor found for arguments: " + Argument.toString(arguments)
                            )
                        );
                    this.constructor = constructor;
                }
            }
        }
        return constructor;
    }

    /**
     * @param theConstructor The constructor
     * @param argumentTypes  The argument types
     * @param args           The arguments
     * @param <T>            The constructor type
     * @return The constructor instance
     */
    static <T> T invokeConstructor(Constructor<T> theConstructor, Argument[] argumentTypes, Object... args) {
        theConstructor.setAccessible(true);
        if (argumentTypes.length == 0) {
            try {
                return theConstructor.newInstance();
            } catch (Throwable e) {
                throw new BeanInstantiationException("Cannot instantiate bean of type [" + theConstructor.getDeclaringClass().getName() + "] using constructor [" + theConstructor + "]:" + e.getMessage(), e);
            }
        } else {
            if (argumentTypes.length != args.length) {
                throw new BeanInstantiationException("Invalid bean argument count specified. Required: " + argumentTypes.length + " . Received: " + args.length);
            }

            for (int i = 0; i < argumentTypes.length; i++) {
                Argument componentType = argumentTypes[i];
                if (!componentType.getType().isInstance(args[i])) {
                    throw new BeanInstantiationException("Invalid bean argument received [" + args[i] + "] at position [" + i + "]. Required type is: " + componentType.getName());
                }
            }
            try {
                return theConstructor.newInstance(args);
            } catch (Throwable e) {
                throw new BeanInstantiationException("Cannot instantiate bean of type [" + theConstructor.getDeclaringClass().getName() + "] using constructor [" + theConstructor + "]:" + e.getMessage(), e);
            }
        }
    }

}
