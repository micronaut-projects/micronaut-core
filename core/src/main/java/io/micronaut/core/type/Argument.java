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

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
     * Constant for {@code List<String>} argument.
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
    @NonNull
    String getName();

    /**
     * Whether the types are equivalent. The regular {@link Object#equals(Object)} implementation includes the argument
     * name within the comparison so this method offers a variation that just compares types.
     *
     * @param other The type
     * @return True if they are equal
     */
    boolean equalsType(@Nullable Argument<?> other);

    /**
     * The hash code including only the types. The regular {@link Object#hashCode()} implementation includes the
     * argument name within the comparison so this method offers a variation that just compares types.
     *
     * @return The type hash code
     */
    int typeHashCode();

    /**
     * Whether this argument is a type variable used in generics.
     *
     * @return True if it is a variable
     * @since 3.0.0
     */
    default boolean isTypeVariable() {
        return false;
    }

    /**
     * Whether the given argument is an instance.
     *
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
     * Delegates to {@link Class#isAssignableFrom(Class)} for this argument.
     *
     * @param candidateType The candidate type
     * @return True if it is assignable from.
     * @since 3.0.0
     */
    default boolean isAssignableFrom(@NonNull Class<?> candidateType) {
        return getType().isAssignableFrom(Objects.requireNonNull(candidateType, "Candidate type cannot be null"));
    }

    /**
     * Checks if the argument can be assigned to this argument.
     *
     * @param candidateArgument The candidate argument
     * @return True if it is assignable from.
     * @since 3.0.0
     */
    default boolean isAssignableFrom(@NonNull Argument<?> candidateArgument) {
        Objects.requireNonNull(candidateArgument, "Candidate type cannot be null");
        if (!isAssignableFrom(candidateArgument.getType())) {
            return false;
        }
        Argument[] typeParameters = getTypeParameters();
        Argument[] candidateArgumentTypeParameters = candidateArgument.getTypeParameters();
        if (typeParameters.length == 0) {
            // Wildcard or no type parameters
            return candidateArgumentTypeParameters.length >= 0;
        }
        if (candidateArgumentTypeParameters.length == 0) {
            for (Argument typeParameter : typeParameters) {
                if (typeParameter.getType() != Object.class) {
                    return false;
                }
            }
            // Wildcard
            return true;
        }
        for (int i = 0; i < typeParameters.length; i++) {
            Argument typeParameter = typeParameters[i];
            Argument candidateArgumentTypeParameter = candidateArgumentTypeParameters[i];
            if (!typeParameter.isAssignableFrom(candidateArgumentTypeParameter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert an argument array to a class array.
     *
     * @param arguments The arguments
     * @return The class array
     */
    static @NonNull
    Class<?>[] toClassArray(@Nullable Argument<?>... arguments) {
        if (ArrayUtils.isEmpty(arguments)) {
            return ReflectionUtils.EMPTY_CLASS_ARRAY;
        }
        Class<?>[] types = new Class[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
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
    static @NonNull
    String toString(@Nullable Argument<?>... arguments) {
        if (ArrayUtils.isNotEmpty(arguments)) {
            StringBuilder baseString = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                baseString.append(argument.toString());
                if (i != arguments.length - 1) {
                    baseString.append(',');
                }
            }
            return baseString.toString();
        } else {
            return "";
        }
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
            @NonNull Class<T> type,
            @Nullable String name,
            @Nullable Argument<?>... typeParameters) {
        return new DefaultArgument<>(type, name, AnnotationMetadata.EMPTY_METADATA, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name that is a type variable.
     *
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param typeParameters     the type parameters
     * @param <T>                The generic type
     * @return The argument instance
     * @since 3.0.0
     */
    @UsedByGeneratedCode
    @NonNull
    static <T> Argument<T> ofTypeVariable(
            @NonNull Class<T> type,
            @Nullable String name,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument<?>... typeParameters) {
        return new DefaultGenericPlaceholder<>(type, name, annotationMetadata, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name that is a type variable.
     *
     * @param type               The type
     * @param argumentName       The name of the argumennt
     * @param variableName       The variable name
     * @param annotationMetadata The annotation metadata
     * @param typeParameters     the type parameters
     * @param <T>                The generic type
     * @return The argument instance
     * @since 3.2.0
     */
    @UsedByGeneratedCode
    @NonNull
    static <T> Argument<T> ofTypeVariable(
            @NonNull Class<T> type,
            @Nullable String argumentName,
            @NonNull String variableName,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument<?>... typeParameters) {
        Objects.requireNonNull(variableName, "Variable name cannot be null");
        return new DefaultGenericPlaceholder<>(
                type,
                argumentName,
                variableName,
                annotationMetadata,
                typeParameters
        );
    }

    /**
     * Creates a new argument for the given type and name that is a type variable.
     *
     * @param type The type
     * @param name The name
     * @param <T>  The generic type
     * @return The argument instance
     * @since 3.0.0
     */
    @UsedByGeneratedCode
    @NonNull
    static <T> Argument<T> ofTypeVariable(
            @NonNull Class<T> type,
            @Nullable String name) {
        return new DefaultGenericPlaceholder<>(type, name, AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * Creates a new argument for the given type and name that is a type variable.
     *
     * @param type The type
     * @param argumentName The name of the argument
     * @param variableName The variable name
     * @param <T>  The generic type
     * @return The argument instance
     * @since 3.2.0
     */
    @UsedByGeneratedCode
    @NonNull
    static <T> Argument<T> ofTypeVariable(
            @NonNull Class<T> type,
            @Nullable String argumentName,
            @NonNull String variableName) {
        return new DefaultGenericPlaceholder<>(type, argumentName, variableName, AnnotationMetadata.EMPTY_METADATA);
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
            @NonNull Class<T> type,
            @Nullable String name,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument<?>... typeParameters) {
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
            @NonNull Class<T> type,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument<?>... typeParameters) {
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
            @NonNull Class<T> type,
            @Nullable String name) {
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
            @NonNull Class<T> type,
            @Nullable Argument<?>... typeParameters) {
        if (ArrayUtils.isEmpty(typeParameters)) {
            return of(type);
        }
        return new DefaultArgument<>(type, null, AnnotationMetadata.EMPTY_METADATA, typeParameters);
    }

    /**
     * Allows coercing a {@link Type} instance to an {@link Argument}.
     *
     * @param type The type
     * @return The argument
     * @throws IllegalArgumentException If the type cannot be coerced
     * @since 3.0.0
     */
    static @NonNull
    Argument<?> of(@NonNull Type type) {
        Objects.requireNonNull(type, "Type cannot be null");
        if (type instanceof Class class1) {
            return Argument.of(class1);
        } else if (type instanceof ParameterizedType pt) {
            final Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> rawClass) {
                final Type[] actualTypeArguments = pt.getActualTypeArguments();
                if (ArrayUtils.isNotEmpty(actualTypeArguments)) {
                    Argument<?>[] typeArguments = new Argument[actualTypeArguments.length];
                    for (int i = 0; i < actualTypeArguments.length; i++) {
                        Type typeArgument = actualTypeArguments[i];
                        if (typeArgument instanceof Class || typeArgument instanceof ParameterizedType) {
                            typeArguments[i] = of(typeArgument);
                        } else {
                            return Argument.of(rawClass);
                        }
                    }
                    return Argument.of(rawClass, typeArguments);
                } else {
                    return Argument.of(rawClass);
                }
            } else {
                throw new IllegalArgumentException("A ParameterizedType that has a raw type that is not a class cannot be converted to an argument");
            }

        } else {
            throw new IllegalArgumentException("Type [" + type + "] must be a Class or ParameterizedType");
        }
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
            @NonNull Class<T> type) {
        return new DefaultArgument<>(type, null, AnnotationMetadata.EMPTY_METADATA, Collections.emptyMap(), Argument.ZERO_ARGUMENTS);
    }

    /**
     * Creates a new argument for the type of the given instance.
     *
     * @param instance The argument instance
     * @param <T>      The generic type
     * @return The argument instance
     * @since 4.6
     */
    @NonNull
    static <T> Argument<T> of(T instance) {
        return Argument.of((Class<T>) instance.getClass());
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
    static <T> Argument<T> of(@NonNull Class<T> type, @Nullable Class<?>... typeParameters) {
        return of(type, AnnotationMetadata.EMPTY_METADATA, typeParameters);
    }

    /**
     * Creates a new argument for the given type and name.
     * NOTE: This method should be avoided as it does use the reflection to retrieve the type parameter names.
     *
     * @param type               The type
     * @param annotationMetadata The annotation metadata
     * @param typeParameters     The parameters type
     * @param <T>                The generic type
     * @return The argument instance
     * @since 3.0.0
     */
    @UsedByGeneratedCode
    @NonNull
    static <T> Argument<T> of(@NonNull Class<T> type, @Nullable AnnotationMetadata annotationMetadata, @Nullable Class<?>[] typeParameters) {
        if (ArrayUtils.isEmpty(typeParameters)) {
            return of(type, annotationMetadata);
        }

        TypeVariable<Class<T>>[] parameters = type.getTypeParameters();
        int len = typeParameters.length;
        if (parameters.length != len) {
            throw new IllegalArgumentException("Type parameter length does not match. Required: " + parameters.length + ", Specified: " + len);
        }
        Argument<?>[] typeArguments = new Argument[len];
        for (int i = 0; i < parameters.length; i++) {
            TypeVariable<Class<T>> parameter = parameters[i];
            typeArguments[i] = Argument.ofTypeVariable(typeParameters[i], parameter.getName());
        }
        return new DefaultArgument<>(type, annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA, typeArguments);
    }

    /**
     * Creates a new argument representing a generic list.
     *
     * @param type list element type
     * @param <T>  list element type
     * @return The argument instance
     */
    @NonNull
    static <T> Argument<List<T>> listOf(@NonNull Class<T> type) {
        return listOf(Argument.of(type, "E"));
    }

    /**
     * Creates a new argument representing a generic list.
     *
     * @param type list element type
     * @param <T>  list element type
     * @return The argument instance
     * @since 2.0.1
     */
    @NonNull
    static <T> Argument<List<T>> listOf(@NonNull Argument<T> type) {
        //noinspection unchecked
        return of((Class<List<T>>) ((Class) List.class), "list", type);
    }

    /**
     * Creates a new argument representing a generic set.
     *
     * @param type set element type
     * @param <T>  set element type
     * @return The argument instance
     */
    @NonNull
    static <T> Argument<Set<T>> setOf(@NonNull Class<T> type) {
        return setOf(Argument.of(type, "E"));
    }

    /**
     * Creates a new argument representing a generic set.
     *
     * @param type set element type
     * @param <T>  set element type
     * @return The argument instance
     * @since 2.0.1
     */
    @NonNull
    static <T> Argument<Set<T>> setOf(@NonNull Argument<T> type) {
        //noinspection unchecked
        return of((Class<Set<T>>) ((Class) Set.class), "set", type);
    }

    /**
     * Creates a new argument representing a generic map.
     *
     * @param keyType   The key type
     * @param valueType The value type
     * @param <K>       The map key type
     * @param <V>       The map value type
     * @return The argument instance
     */
    @NonNull
    static <K, V> Argument<Map<K, V>> mapOf(@NonNull Class<K> keyType, @NonNull Class<V> valueType) {
        return mapOf(Argument.of(keyType, "K"), Argument.of(valueType, "V"));
    }

    /**
     * Creates a new argument representing a generic map.
     *
     * @param keyType   The key type
     * @param valueType The value type
     * @param <K>       The map key type
     * @param <V>       The map value type
     * @return The argument instance
     * @since 2.0.1
     */
    @NonNull
    static <K, V> Argument<Map<K, V>> mapOf(@NonNull Argument<K> keyType, @NonNull Argument<V> valueType) {
        //noinspection unchecked
        return of((Class<Map<K, V>>) ((Class) Map.class), "map", keyType, valueType);
    }

    /**
     * Creates a new argument representing an optional.
     *
     * @param optionalValueClass   The optional type
     * @param <T>       The optional type
     * @return The argument instance
     * @since 4.0.0
     */
    @NonNull
    static <T> Argument<Optional<T>> optionalOf(@NonNull Class<T> optionalValueClass) {
        return optionalOf(Argument.of(optionalValueClass, "T"));
    }

    /**
     * Creates a new argument representing an optional.
     *
     * @param optionalValueArgument   The optional type
     * @param <T>       The optional type
     * @return The argument instance
     * @since 4.0.0
     */
    @NonNull
    static <T> Argument<Optional<T>> optionalOf(@NonNull Argument<T> optionalValueArgument) {
        //noinspection unchecked
        return of((Class<Optional<T>>) ((Class) Optional.class), "optional", optionalValueArgument);
    }

}
