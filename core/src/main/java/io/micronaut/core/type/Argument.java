/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.type;

import io.micronaut.core.annotation.*;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents an argument to a method or constructor or type.
 *
 * @param <T> The argument type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> extends TypeInformation<T>, AnnotatedElement, Type {

    /**
     * Constant for string argument.
     */
    Argument<String> STRING = Argument.of(String.class);

    /**
     * Constant for int argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Integer> INT = Argument.of(int.class);

    /**
     * Constant for long argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Long> LONG = Argument.of(long.class);

    /**
     * Constant for float argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Float> FLOAT = Argument.of(float.class);

    /**
     * Constant for double argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Double> DOUBLE = Argument.of(double.class);

    /**
     * Constant for void argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Void> VOID = Argument.of(void.class);

    /**
     * Constant for byte argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Byte> BYTE = Argument.of(byte.class);

    /**
     * Constant for boolean argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Boolean> BOOLEAN = Argument.of(boolean.class);

    /**
     * Constant char argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Character> CHAR = Argument.of(char.class);

    /**
     * Constant short argument. Used by generated code, do not remove.
     */
    @SuppressWarnings("unused")
    Argument<Short> SHORT = Argument.of(short.class);

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
     * Constant for List<String> argument.
     */
    Argument<List<String>> LIST_OF_STRING = Argument.listOf(String.class);

    /**
     * Constant for Void object argument.
     */
    Argument<Void> VOID_OBJECT = Argument.of(Void.class);

    /**
     * @return The name of the argument
     */
    @Override
    @NonNull String getName();

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
     * Convert an argument array to a class array.
     *
     * @param arguments The arguments
     * @return The class array
     */
    static @NonNull Class[] toClassArray(Argument... arguments) {
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
    static @NonNull String toString(Argument... arguments) {
        StringBuilder baseString = new StringBuilder();
        if (ArrayUtils.isNotEmpty(arguments)) {
            for (int i = 0; i < arguments.length; i++) {
                Argument argument = arguments[i];
                baseString.append(argument.toString());
                if (i != arguments.length - 1) {
                    baseString.append(',');
                }
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
    @NonNull
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
    @NonNull
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
     * @param type               The type
     * @param annotationMetadata the annotation metadata
     * @param typeParameters     the type parameters
     * @param <T>                The generic type
     * @return The argument instance
     */
    @UsedByGeneratedCode
    @NonNull
    static <T> Argument<T> of(
            Class<T> type,
            AnnotationMetadata annotationMetadata,
            @Nullable Argument... typeParameters) {
        return new DefaultArgument<>(type, annotationMetadata, typeParameters);
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
    @NonNull
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
    @NonNull
    static <T> Argument<T> of(
        Class<T> type, @Nullable Argument... typeParameters) {
        if (ArrayUtils.isEmpty(typeParameters)) {
            return of(type);
        }
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
    @NonNull
    static <T> Argument<T> of(
        Class<T> type) {
        return new DefaultArgument<>(type, AnnotationMetadata.EMPTY_METADATA, Argument.ZERO_ARGUMENTS);
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
    @NonNull
    static <T> Argument<T> of(Class<T> type, @Nullable Class<?>... typeParameters) {
        if (ArrayUtils.isEmpty(typeParameters)) {
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
        return new DefaultArgument<>(type, AnnotationMetadata.EMPTY_METADATA, typeArguments);
    }

    /**
     * Creates a new argument representing a generic list.
     *
     * @param type list element type
     * @param <T>  list element type
     * @return The argument instance
     */
    @NonNull
    static <T> Argument<List<T>> listOf(Class<T> type) {
        //noinspection unchecked
        return of((Class<List<T>>) ((Class) List.class), type);
    }

    /**
     * Creates a new argument representing a generic list.
     *
     * @param type list element type
     * @param <T>  list element type
     * @since 2.0.1
     * @return The argument instance
     */
    @NonNull
    static <T> Argument<List<T>> listOf(Argument<T> type) {
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
    @NonNull
    static <T> Argument<Set<T>> setOf(Class<T> type) {
        //noinspection unchecked
        return of((Class<Set<T>>) ((Class) Set.class), type);
    }

    /**
     * Creates a new argument representing a generic set.
     *
     * @param type set element type
     * @param <T>  set element type
     * @since 2.0.1
     * @return The argument instance
     */
    @NonNull
    static <T> Argument<Set<T>> setOf(Argument<T> type) {
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
    @NonNull
    static <K, V> Argument<Map<K, V>> mapOf(Class<K> keyType, Class<V> valueType) {
        //noinspection unchecked
        return of((Class<Map<K, V>>) ((Class) Map.class), keyType, valueType);
    }

    /**
     * Creates a new argument representing a generic map.
     *
     * @param keyType The key type
     * @param valueType The value type
     * @param <K>  The map key type
     * @param <V> The map value type
     * @since 2.0.1
     * @return The argument instance
     */
    @NonNull
    static <K, V> Argument<Map<K, V>> mapOf(Argument<K> keyType, Argument<V> valueType) {
        //noinspection unchecked
        return of((Class<Map<K, V>>) ((Class) Map.class), keyType, valueType);
    }

}
