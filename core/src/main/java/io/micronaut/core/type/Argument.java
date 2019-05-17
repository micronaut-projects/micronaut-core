/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;

import javax.annotation.Nullable;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents an argument to a method or constructor or type.
 *
 * @param <T> The argument type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> extends TypeVariableResolver, AnnotatedElement {

    /**
     * Constant for int argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument INT = Argument.of(int.class);

    /**
     * Constant for long argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument LONG = Argument.of(long.class);

    /**
     * Constant for float argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument FLOAT = Argument.of(float.class);

    /**
     * Constant for double argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument DOUBLE = Argument.of(double.class);

    /**
     * Constant for void argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument VOID = Argument.of(void.class);

    /**
     * Constant for byte argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument BYTE = Argument.of(byte.class);

    /**
     * Constant for boolean argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument BOOLEAN = Argument.of(boolean.class);

    /**
     * Constant char argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument CHAR = Argument.of(char.class);

    /**
     * Constant short argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument SHORT = Argument.of(short.class);

    /**
     * Constant representing zero arguments. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    @UsedByGeneratedCode
    Argument[] ZERO_ARGUMENTS = new Argument[0];

    /**
     * Default Object argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
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
     * Whether the given argument is an instance.
     * @param o The object
     * @return True if it is an instance of this type
     */
    default boolean isInstance(@Nullable Object o) {
        if (o == null) {
            return false;
        }
        return getType().isInstance(o);
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

    /**
     * Convert an argument array to a class array.
     *
     * @param arguments The arguments
     * @return The class array
     */
    static Class[] toClassArray(Argument... arguments) {
        if (ArrayUtils.isEmpty(arguments)) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        }
        Class[] types = new Class[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Argument argument = arguments[i];
            types[i] = argument.getType();
        }
        return types;
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
     * Creates a new argument for the given type and name.
     *
     * @param type           The type
     * @param name           The name
     * @param typeParameters the type parameters
     * @param <T>            The generic type
     * @return The argument instance
     */
    @UsedByGeneratedCode
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
    @UsedByGeneratedCode
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
    @UsedByGeneratedCode
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
    @UsedByGeneratedCode
    static <T> Argument<T> of(
        Class<T> type, @Nullable Argument... typeParameters) {
        return new DefaultArgument<>(type, NameUtils.decapitalize(type.getSimpleName()), AnnotationMetadata.EMPTY_METADATA, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name.
     *
     * @param type The type
     * @param <T>  The generic type
     * @return The argument instance
     */
    @UsedByGeneratedCode
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
    @UsedByGeneratedCode
    static <T> Argument<T> of(Class<T> type, @Nullable Class<?>... typeParameters) {
        if (typeParameters == null) {
            return of(type);
        }

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
        return new DefaultArgument<>(type, NameUtils.decapitalize(type.getSimpleName()), AnnotationMetadata.EMPTY_METADATA, typeArguments);
    }

    /**
     * Creates a new argument representing a generic list.
     *
     * @param type list element type
     * @param <T>  list element type
     * @return The argument instance
     */
    static <T> Argument<List<T>> listOf(Class<T> type) {
        //noinspection unchecked
        return of((Class<List<T>>) ((Class) List.class), type);
    }

    /**
     * Creates a new argument representing a generic set.
     *
     * @param type set element type
     * @param <T>  set element type
     * @return The argument instance
     */
    static <T> Argument<Set<T>> setOf(Class<T> type) {
        //noinspection unchecked
        return of((Class<Set<T>>) ((Class) Set.class), type);
    }

    /**
     * Creates a new argument representing a generic map.
     *
     * @param keyType The key type
     * @param valueType The value type
     * @param <K>  The map key type
     * @param <V> The map value type
     * @return The argument instance
     */
    static <K, V> Argument<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
        //noinspection unchecked
        return of((Class<Map<K, V>>) ((Class) Map.class), keyType, valueType);
    }
}
