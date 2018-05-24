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

package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationSource;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents an argument to a method or constructor or type.
 *
 * @param <T> The argument type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> extends AnnotationSource, TypeVariableResolver, Named, AnnotationMetadataProvider {

    /**
     * Constant representing zero arguments.
     */
    Argument[] ZERO_ARGUMENTS = new Argument[0];

    /**
     * Default Object argument.
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
     * Whether the types are equivalent. The regular {@link Object#equals(Object)} implementation includes the argument
     * name within the comparison so this method offers a variation that just compares types.
     *
     * @param other The type type
     * @return True if they are equal
     */
    boolean equalsType(Argument<?> other);

    /**
     * The hash code including only the types. The regular {@link Object#hashCode()} implementation includes the
     * argument name within the comparison so this method offers a variation that just compares types.
     *
     * @return The type hash code
     */
    int typeHashCode();

    /**
     * Convert an argument array to a class array.
     *
     * @param arguments The arguments
     * @return The class array
     */
    static Class[] toClassArray(Argument... arguments) {
        if (ArrayUtils.isEmpty(arguments)) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        } else {
            Class[] types = new Class[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Argument argument = arguments[i];
                types[i] = argument.getType();
            }
            return types;
        }
    }

    /**
     * Convert the arguments to a string representation.
     *
     * @param arguments The arguments
     * @return The String representation
     */
    static String toString(Argument... arguments) {
        StringBuilder baseString = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = arguments[i];
            baseString.append(argument.toString());
            if (i != arguments.length - 1) {
                baseString.append(',');
            }
        }
        return baseString.toString();
    }

    /**
     * Creates a new argument for the given type, name and qualifier.
     *
     * @param type      The type
     * @param name      The name
     * @param qualifier The qualifier
     * @param <T>       The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
        Class<T> type,
        String name,
        Annotation qualifier) {
        return new DefaultArgument<>(type, name, qualifier);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type           The type
     * @param name           The name
     * @param typeParameters the type parameters
     * @param <T>            The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
        Class<T> type,
        String name,
        @Nullable Argument... typeParameters) {
        return new DefaultArgument<>(type, name, AnnotationMetadata.EMPTY_METADATA, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata the annotation metadata
     * @param typeParameters     the type parameters
     * @param <T>                The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
        Class<T> type,
        String name,
        AnnotationMetadata annotationMetadata,
        @Nullable Argument... typeParameters) {
        return new DefaultArgument<>(type, name, annotationMetadata, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type The type
     * @param name The name
     * @param <T>  The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
        Class<T> type,
        String name) {
        return new DefaultArgument<>(type, name, AnnotationMetadata.EMPTY_METADATA, Argument.ZERO_ARGUMENTS);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type           The type
     * @param typeParameters The parameters type
     * @param <T>            The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
        Class<T> type, @Nullable Argument... typeParameters) {
        return new DefaultArgument<>(type, type.getSimpleName(), AnnotationMetadata.EMPTY_METADATA, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type The type
     * @param <T>  The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(
        Class<T> type) {
        return new DefaultArgument<>(type, NameUtils.decapitalize(type.getSimpleName()), AnnotationMetadata.EMPTY_METADATA, Argument.ZERO_ARGUMENTS);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type           The type
     * @param typeParameters the parameters type
     * @param <T>            The generic type
     * @return The argument instance
     */
    static <T> Argument<T> of(Class<T> type, @Nullable Class<?>... typeParameters) {
        if (typeParameters == null) {
            return of(type);
        } else {

            TypeVariable<Class<T>>[] parameters = type.getTypeParameters();
            int len = typeParameters.length;
            if (parameters.length != len) {
                throw new IllegalArgumentException("Type parameter length does not match. Required: " + parameters.length + ", Specified: " + len);
            }
            Argument[] typeArguments = new Argument[len];
            for (int i = 0; i < parameters.length; i++) {
                TypeVariable<Class<T>> parameter = parameters[i];
                typeArguments[i] = Argument.of(typeParameters[i], parameter.getName());
            }
            return new DefaultArgument<>(type, type.getSimpleName(), AnnotationMetadata.EMPTY_METADATA, typeArguments);
        }
    }

    /**
     * Create a new argument for the given method.
     *
     * @param method         The method
     * @param name           The argument name
     * @param index          The argument index
     * @param qualifierType  The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    @SuppressWarnings("unchecked")
    static Argument of(
        Method method,
        String name,
        int index,
        @Nullable Class qualifierType,
        @Nullable Argument... typeParameters) {
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
     * Create a new argument for the given constructor.
     *
     * @param constructor    The method
     * @param name           The argument name
     * @param index          The argument index
     * @param qualifierType  The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    @SuppressWarnings("unchecked")
    static Argument of(
        Constructor constructor,
        String name,
        int index,
        @Nullable Class qualifierType,
        @Nullable Argument... typeParameters) {
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
     * Create a new argument for the given field.
     *
     * @param field          The field
     * @param name           The argument name
     * @param qualifierType  The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    @SuppressWarnings("unchecked")
    static Argument of(
        Field field,
        String name,
        @Nullable Class qualifierType,
        @Nullable Argument... typeParameters) {
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
     * Create a new argument for the given field.
     *
     * @param field          The field
     * @param qualifierType  The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    @SuppressWarnings("unchecked")
    static Argument of(
        Field field,
        @Nullable Class qualifierType,
        @Nullable Argument... typeParameters) {
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
     * Create a new argument for the given field.
     *
     * @param field          The field
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    @SuppressWarnings("unchecked")
    static Argument of(
        Field field,
        @Nullable Argument... typeParameters) {
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

    /**
     * Returns the string representation of the argument type, including generics.
     *
     * @param simple If true, output the simple name of types
     * @return The type string representation
     */
    default String getTypeString(boolean simple) {
        Class<T> type = getType();
        StringBuilder returnType = new StringBuilder(simple ? type.getSimpleName() : type.getName());
        Map<String, Argument<?>> generics = getTypeVariables();
        if (!generics.isEmpty()) {
            returnType
                .append("<")
                .append(generics.values()
                    .stream()
                    .map(arg -> arg.getTypeString(simple))
                    .collect(Collectors.joining(", ")))
                .append(">");
        }
        return returnType.toString();
    }

    /**
     * @return Whether the argument has any type variables
     */
    default boolean hasTypeVariables() {
        return !getTypeVariables().isEmpty();
    }
}
