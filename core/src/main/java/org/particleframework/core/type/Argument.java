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
package org.particleframework.core.type;

import org.particleframework.core.annotation.AnnotationSource;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Nullable;
import org.particleframework.core.naming.Named;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;

/**
 * Represents an argument to a method or constructor or type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> extends AnnotationSource, TypeVariableResolver, Named {
    /**
     * Constant representing zero arguments
     */
    Argument[] ZERO_ARGUMENTS = new Argument[0];

    /**
     * Default Object argument
     */
    Argument<Object> OBJECT_ARGUMENT = of(Object.class);

    /**
     * @return The name of the argument
     */
    String getName();

    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The qualifier or null if there is none
     */
    Annotation getQualifier();

    /**
     * Creates a new argument for the given type, name and qualifier
     *
     * @param type The type
     * @param name The name
     * @param qualifier The qualifier
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
            Class<T> type,
            String name,
            Annotation qualifier) {
        return new DefaultArgument<>(type, name, qualifier);
    }

    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param name The name
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
            Class<T> type,
            String name,
            @Nullable Argument...typeParameters) {
        return new DefaultArgument<>(type, name, null, typeParameters);
    }


    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param name The name
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
            Class<T> type,
            String name) {
        return new DefaultArgument<>(type, name, null, Argument.ZERO_ARGUMENTS);
    }

    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
            Class<T> type, @Nullable Argument...typeParameters) {
        return new DefaultArgument<>(type, type.getSimpleName(), null, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
            Class<T> type) {
        return new DefaultArgument<>(type, type.getSimpleName(), null, Argument.ZERO_ARGUMENTS);
    }
    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
            Class<T> type, @Nullable Class<?>...typeParameters) {
        if(typeParameters == null) {
            return of(type);
        }
        else {

            TypeVariable<Class<T>>[] parameters = type.getTypeParameters();
            int len = typeParameters.length;
            if(parameters.length != len) {
                throw new IllegalArgumentException("Type parameter length does not match. Required: " + parameters.length + ", Specified: " + len);
            }
            Argument[] typeArguments = new Argument[len];
            for (int i = 0; i < parameters.length; i++) {
                TypeVariable<Class<T>> parameter = parameters[i];
                typeArguments[i] = Argument.of(typeParameters[i], parameter.getName());
            }
            return new DefaultArgument<>(type, type.getSimpleName(), null, typeArguments);
        }
    }

    /**
     * Create a new argument for the given method
     *
     * @param method The method
     * @param name The argument name
     * @param index The argument index
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument of(
            Method method,
            String name,
            int index,
            @Nullable Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = method.getParameterTypes()[index];

        Annotation[] annotations = method.getParameterAnnotations()[index];
        Annotation annotation = (Annotation) AnnotationUtil.findAnnotation(annotations, qualifierType).orElse(null);
        return new DefaultArgument(
                type,
                name,
                annotation,
                annotations,
                typeParameters
        );
    }


    /**
     * Create a new argument for the given constructor
     *
     * @param constructor The method
     * @param name The argument name
     * @param index The argument index
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument of(
            Constructor constructor,
            String name,
            int index,
            @Nullable Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = constructor.getParameterTypes()[index];

        Annotation[] annotations = constructor.getParameterAnnotations()[index];
        Annotation annotation = (Annotation) AnnotationUtil.findAnnotation(annotations, qualifierType).orElse(null);
        return new DefaultArgument(
                type,
                name,
                annotation,
                annotations,
                typeParameters
        );
    }

    /**
     * Create a new argument for the given field
     *
     * @param field The field
     * @param name The argument name
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument of(
            Field field,
            String name,
            @Nullable Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = field.getType();
        Annotation[] annotations = field.getAnnotations();
        Annotation annotation = (Annotation) AnnotationUtil.findAnnotation(annotations, qualifierType).orElse(null);
        return new DefaultArgument(
                type,
                name,
                annotation,
                annotations,
                typeParameters
        );
    }

    /**
     * Create a new argument for the given field
     *
     * @param field The field
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument of(
            Field field,
            @Nullable Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = field.getType();
        Annotation[] annotations = field.getAnnotations();
        Annotation annotation = (Annotation) AnnotationUtil.findAnnotation(annotations, qualifierType).orElse(null);
        return new DefaultArgument(
                type,
                field.getName(),
                annotation,
                annotations,
                typeParameters
        );
    }

    /**
     * Create a new argument for the given field
     *
     * @param field The field
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument of(
            Field field,
            @Nullable Argument...typeParameters) {
        Class type = field.getType();
        Annotation[] annotations = field.getAnnotations();
        return new DefaultArgument(
                type,
                field.getName(),
                null,
                annotations,
                typeParameters
        );
    }
}