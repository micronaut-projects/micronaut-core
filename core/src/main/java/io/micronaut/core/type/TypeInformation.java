/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Provides information about a type at runtime.
 *
 * @author graemerocher
 * @param <T> The generic type
 * @since 2.4.0
 */
public interface TypeInformation<T> extends TypeVariableResolver, AnnotationMetadataProvider, Type {
    /**
     * @return The type
     */
    @NonNull Class<T> getType();

    /**
     * @return Is the type primitive.
     * @since 3.0.0
     */
    default boolean isPrimitive() {
        return getType().isPrimitive();
    }

    /**
     * If the type is primitive returns the wrapper type, otherwise returns the actual type.
     * @return The wrapper type if primitive
     */
    default Class<?> getWrapperType() {
        if (isPrimitive()) {
            return ReflectionUtils.getWrapperType(getType());
        } else {
            return getType();
        }
    }

    @Override
    @NonNull
    default String getTypeName() {
        Argument<?>[] typeParameters = getTypeParameters();
        if (ArrayUtils.isNotEmpty(typeParameters)) {
            String typeName = getType().getTypeName();
            return typeName +  "<" + Arrays.stream(typeParameters).map(Argument::getTypeName).collect(Collectors.joining(",")) + ">";
        } else {
            return getType().getTypeName();
        }
    }

    /**
     * @return Is the return type reactive.
     * @since 2.0.0
     */
    default boolean isReactive() {
        return RuntimeTypeInformation.isReactive(getType());
    }

    /**
     * Returns whether this type is a wrapper type that wraps the actual type such as a Optional or a Response wrapper.
     *
     * @return True if it is a wrapper type.
     * @since 2.4.0
     */
    default boolean isWrapperType() {
        return RuntimeTypeInformation.isWrapperType(getType());
    }

    /**
     * Returns the wrapped type in the case where {@link #isWrapperType()} returns true.
     * @return The wrapped type
     */
    default Argument<?> getWrappedType() {
        return RuntimeTypeInformation.getWrappedType(this);
    }

    /**
     * @return Is the return the return type a reactive completable type.
     * @since 2.0.0
     */
    default boolean isCompletable() {
        return RuntimeTypeInformation.isCompletable(getType());
    }

    /**
     * @return Is the return type asynchronous.
     * @since 2.0.0
     */
    default boolean isAsync() {
        Class<T> type = getType();
        return CompletionStage.class.isAssignableFrom(type);
    }

    /**
     * @return Is the return type either async or reactive.
     * @since 2.0.0
     */
    default boolean isAsyncOrReactive() {
        return isAsync() || isReactive();
    }

    /**
     * @return Whether this is a container type.
     */
    default boolean isContainerType() {
        final Class<T> type = getType();
        return Map.class == type ||  DefaultArgument.CONTAINER_TYPES.contains(type);
    }

    /**
     * @return Whether the argument has any type variables
     */
    default boolean hasTypeVariables() {
        return !getTypeVariables().isEmpty();
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
     * Returns whether the return type is logically void. This includes
     * reactive times that emit nothing (such as {@code io.micronaut.core.async.subscriber.Completable})
     * and asynchronous types that emit {@link Void}.
     *
     * @return Is the return type logically void.
     * @since 2.0.0
     */
    default boolean isVoid() {
        Class<T> javaReturnType = getType();
        if (javaReturnType == void.class) {
            return true;
        } else {
            if (isCompletable()) {
                return true;
            }
            if (isReactive() || isAsync()) {
                return getFirstTypeVariable().filter(arg -> arg.getType() == Void.class).isPresent();
            }
        }
        return false;
    }

    /**
     * @return Is the return type {@link java.util.Optional}.
     * @since 2.0.1
     */
    default boolean isOptional() {
        Class<T> type = getType();
        return type == Optional.class;
    }

    /**
     * @return Has the return type been specified to emit a single result with {@code SingleResult}.
     * @since 2.0
     */
    default boolean isSpecifiedSingle() {
        return RuntimeTypeInformation.isSpecifiedSingle(this);
    }

    /**
     * Represent this argument as a {@link ParameterizedType}.
     * @return The {@link ParameterizedType}
     * @since 2.0.0
     */
    default @NonNull ParameterizedType asParameterizedType() {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return getTypeParameters();
            }

            @Override
            public Type getRawType() {
                return TypeInformation.this.getType();
            }

            @Override
            public Type getOwnerType() {
                return TypeInformation.this;
            }

            @Override
            public String getTypeName() {
                return TypeInformation.this.getTypeName();
            }

            @Override
            public String toString() {
                return getTypeName();
            }
        };
    }

    /**
     * @return Is the type an array.
     * @since 2.4.0
     */
    default boolean isArray() {
        return getType().isArray();
    }

    /**
     * Obtains the type's simple name.
     * @return The simple name
     * @since 3.0.0
     */
    default @NonNull String getSimpleName() {
        return getType().getSimpleName();
    }

    default boolean isProvider() {
        for (String type: DefaultArgument.PROVIDER_TYPES) {
            if (getType().getName().equals(type)) {
                return true;
            }
        }
        return false;
    }
}
