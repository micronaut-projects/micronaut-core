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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.AnsiColour;
import io.micronaut.core.util.ArrayUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Provides information about a type at runtime.
 *
 * @param <T> The generic type
 * @author graemerocher
 * @since 2.4.0
 */
public interface TypeInformation<T> extends TypeVariableResolver, AnnotationMetadataProvider, Type {
    /**
     * @return The type
     */
    @NonNull
    Class<T> getType();

    /**
     * @return Is the type primitive.
     * @since 3.0.0
     */
    default boolean isPrimitive() {
        return getType().isPrimitive();
    }

    /**
     * If the type is primitive returns the wrapper type, otherwise returns the actual type.
     *
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
            return typeName + "<" + Arrays.stream(typeParameters).map(Argument::getTypeName).collect(Collectors.joining(",")) + ">";
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
     * Returns whether this type is a wrapper type that wraps the actual type such as an Optional or a Response wrapper.
     *
     * @return True if it is a wrapper type.
     * @since 2.4.0
     */
    default boolean isWrapperType() {
        return RuntimeTypeInformation.isWrapperType(getType());
    }

    /**
     * Returns the wrapped type in the case where {@link #isWrapperType()} returns true.
     *
     * @return The wrapped type
     */
    default Argument<?> getWrappedType() {
        return RuntimeTypeInformation.getWrappedType(this);
    }

    /**
     * @return Is the return type a reactive completable type.
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
        return Map.class == type || DefaultArgument.CONTAINER_TYPES.contains(type.getName());
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
        return getTypeString(simple ? TypeFormat.SIMPLE : TypeFormat.QUALIFIED);
    }

    /**
     * Similar to {@link #getTypeString(TypeFormat)} but includes any scopes and qualifiers.
     *
     * @param format The format
     * @return The type string including the scope and qualifier
     * @see #getTypeString(TypeFormat)
     */
    default String getBeanTypeString(@NonNull TypeFormat format) {
        return TypeFormat.getBeanTypeString(
            format,
            getType(),
            getTypeVariables(),
            getAnnotationMetadata()
        );
    }

    /**
     * Returns the string representation of the argument type, including generics.
     *
     * @param format The format.
     * @return The type string representation
     * @since 4.8.0
     */
    default @NonNull String getTypeString(@NonNull TypeFormat format) {
        Class<T> type = getType();
        return TypeFormat.getTypeString(format, type, getTypeVariables());
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
        return RuntimeTypeInformation.isSpecifiedSingle(getType(), this);
    }

    /**
     * Represent this argument as a {@link Type}.
     *
     * @return The {@link Type}
     * @since 3.5.2
     */
    default @NonNull Type asType() {
        if (getTypeParameters().length == 0) {
            return getType();
        }
        return asParameterizedType();
    }

    /**
     * Represent this argument as a {@link ParameterizedType}.
     *
     * @return The {@link ParameterizedType}
     * @since 2.0.0
     */
    default @NonNull ParameterizedType asParameterizedType() {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return Arrays.stream(getTypeParameters()).map(TypeInformation::asType).toArray(Type[]::new);
            }

            @Override
            public Type getRawType() {
                return TypeInformation.this.getType();
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public String getTypeName() {
                return TypeInformation.this.getTypeName();
            }

            @Override
            public String toString() {
                return getTypeName();
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(getActualTypeArguments()) ^ Objects.hashCode(getOwnerType()) ^ Objects.hashCode(getRawType());
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof ParameterizedType that) {
                    if (this == that) {
                        return true;
                    }
                    return Objects.equals(getOwnerType(), that.getOwnerType())
                        && Objects.equals(getRawType(), that.getRawType()) &&
                        Arrays.equals(getActualTypeArguments(), that.getActualTypeArguments());
                }
                return false;
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
     *
     * @return The simple name
     * @since 3.0.0
     */
    default @NonNull String getSimpleName() {
        return getType().getSimpleName();
    }

    default boolean isProvider() {
        for (String type : DefaultArgument.PROVIDER_TYPES) {
            if (getType().getName().equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Type formatting to apply.
     *
     * @see TypeInformation#getTypeString(boolean)
     * @since 4.8.0
     */
    enum TypeFormat {
        /**
         * Simple format.
         */
        SIMPLE,
        /**
         * Qualified format.
         */
        QUALIFIED,
        /**
         * Shorted format.
         */
        SHORTENED,
        /**
         * Simple name highlighted wit ANSI.
         */
        ANSI_SIMPLE,
        /**
         * Qualified name highlighted with ANSI.
         */
        ANSI_QUALIFIED,
        /**
         * Shortened name highlighted with ANSI.
         */
        ANSI_SHORTENED;

        private static final String ANN_CR = "io.micronaut.context.annotation.ConfigurationReader";

        /**
         * Obtain the bean type string.
         *
         * @param typeFormat The type format
         * @param argument   The argument
         * @return The string
         */
        public static @NonNull String getBeanTypeString(
            @NonNull TypeInformation.TypeFormat typeFormat, @NonNull Argument<?> argument) {
            return getBeanTypeString(
                typeFormat,
                argument.getType(),
                argument.getTypeVariables(),
                argument.getAnnotationMetadata()
            );
        }

        /**
         * @return Is an ANSI format.
         */
        public boolean isAnsi() {
            return this == ANSI_SIMPLE ||
                this == ANSI_QUALIFIED ||
                this == ANSI_SHORTENED;
        }

        /**
         * Format the annotation name.
         *
         * @param annotationRef The type name
         * @return The annotation
         */
        public String formatAnnotation(String annotationRef) {
            int i = annotationRef.indexOf("(");
            String members = i > -1 ? annotationRef.substring(i) : "";
            annotationRef = i > -1 ? annotationRef.substring(0, i) : annotationRef;
            return switch (this) {
                case SIMPLE -> "@" + NameUtils.getSimpleName(annotationRef) + members;
                case QUALIFIED -> "@" + annotationRef + members;
                case SHORTENED -> "@" + NameUtils.getShortenedName(annotationRef) + members;
                case ANSI_SIMPLE ->
                    AnsiColour.yellow("@" + NameUtils.getSimpleName(annotationRef)) + members;
                case ANSI_QUALIFIED -> AnsiColour.yellow("@" + annotationRef) + members;
                case ANSI_SHORTENED ->
                    AnsiColour.yellow("@" + NameUtils.getShortenedName(annotationRef)) + members;
            };
        }

        /**
         * Get a type string for the given format.
         *
         * @param format   The format
         * @param type     The type
         * @param generics The generics
         * @return the type string
         */
        public static @NonNull String getTypeString(
            @NonNull TypeFormat format,
            @NonNull Class<?> type,
            @NonNull Map<String, Argument<?>> generics) {
            String typeName = switch (format) {
                case SIMPLE -> type.getSimpleName();
                case QUALIFIED -> type.getCanonicalName();
                case SHORTENED -> NameUtils.getShortenedName(type.getTypeName());
                case ANSI_SIMPLE -> AnsiColour.cyan(type.getSimpleName());
                case ANSI_QUALIFIED -> AnsiColour.cyan(type.getCanonicalName());
                case ANSI_SHORTENED ->
                    AnsiColour.cyan(NameUtils.getShortenedName(type.getCanonicalName()));
            };
            StringBuilder returnType = new StringBuilder(typeName);
            if (!generics.isEmpty()) {
                returnType
                    .append(format.isAnsi() ? AnsiColour.brightCyan("<") : "<")
                    .append(generics.values()
                        .stream()
                        .map(arg -> arg.getTypeString(format))
                        .collect(Collectors.joining(", ")))
                    .append(format.isAnsi() ? AnsiColour.brightCyan(">") : ">");
            }
            return returnType.toString();
        }

        /**
         * Get a type string for the given format.
         *
         * @param format             The format
         * @param type               The type
         * @param generics           The generics
         * @param annotationMetadata The annotation metadata
         * @return the type string
         */
        public static @NonNull String getBeanTypeString(
            @NonNull TypeFormat format,
            @NonNull Class<?> type,
            @NonNull Map<String, Argument<?>> generics,
            @NonNull AnnotationMetadata annotationMetadata) {
            String typeFormat = TypeFormat.getTypeString(
                format,
                type,
                generics
            );
            Optional<String> q = annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER)
                .map(qualifier -> {
                    if (AnnotationUtil.NAMED.equals(qualifier)) {
                        String name = annotationMetadata.stringValue(AnnotationUtil.NAMED).orElse(null);
                        if (name != null) {
                            if (format.isAnsi()) {
                                return qualifier + "(" + AnsiColour.green("\"" + name + "\"") + ")";
                            } else {
                                return qualifier + "(\"" + name + "\")";
                            }
                        }
                    }
                    return qualifier;
                });
            Optional<String> s = annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE)
                .map(scope -> {
                    if (AnnotationUtil.SINGLETON.equals(scope)) {
                        // handle case where @Singleton is used as a meta annotation
                        scope = annotationMetadata.getAnnotationNameByStereotype(scope)
                            .orElse(scope);
                        String configuration = annotationMetadata.stringValue(
                                ANN_CR,
                                "prefix"
                            )
                            .orElse(null);
                        if (configuration != null) {
                            if (format.isAnsi()) {
                                scope = scope + "(" + AnsiColour.green("\"" + configuration + "\"") + ")";
                            } else {
                                scope = scope + "(\"" + configuration + "\")";
                            }
                        }
                    }
                    return scope;
                });
            if (s.isPresent()) {
                typeFormat = format.formatAnnotation(s.get()) + " " + typeFormat;
            }
            if (q.isPresent()) {
                typeFormat = format.formatAnnotation(q.get()) + " " + typeFormat;
            }
            return typeFormat;
        }
    }


}
