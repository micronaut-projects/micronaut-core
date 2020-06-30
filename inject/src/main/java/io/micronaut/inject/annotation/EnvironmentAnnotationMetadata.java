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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;

/**
 * Internal interface representing environment aware annotation metadata.
 *
 * @author graemerocher
 * @since 1.3.0
 */
@Internal
interface EnvironmentAnnotationMetadata extends AnnotationMetadata {
    /**
     * Retrieve the enum value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param enumType The enum type
     * @param valueMapper The value mapper
     * @param <E> The enum type
     * @return The enum value
     */
    @Internal
    <E extends Enum> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the enum value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param enumType The enum type
     * @param valueMapper The value mapper
     * @param <E> The enum type
     * @return The enum value
     */
    @Internal
    <E extends Enum> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the enum values and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param enumType The enum type
     * @param valueMapper The value mapper
     * @param <E> The enum type
     * @return The enum value
     */
    @Internal
    <E extends Enum> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the enum values and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param enumType The enum type
     * @param valueMapper The value mapper
     * @param <E> The enum type
     * @return The enum value
     */
    @Internal
    <E extends Enum> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    @Internal
    Optional<Class> classValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the int value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Internal
    OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
    * Retrieve the boolean value and optionally map its value.
    * @param annotation The annotation
    * @param member The member
    * @param valueMapper The value mapper
    * @return The boolean value
    */
    Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    @NonNull
    Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the long value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @Internal
    OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the long value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @NonNull
    OptionalLong longValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the int value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @NonNull
    OptionalInt intValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @NonNull
    String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The string value
     */
    @NonNull
    Optional<String> stringValue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    boolean isTrue(@NonNull String annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the double value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Internal
    OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the double value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @NonNull
    @Internal
    OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member, Function<Object, Object> valueMapper);

    /**
     * Resolves the given value performing type conversion as necessary.
     * @param annotation The annotation
     * @param member The member
     * @param requiredType The required type
     * @param valueMapper The value mapper
     * @param <T> The generic type
     * @return The resolved value
     */
    @NonNull
    <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper);
}
