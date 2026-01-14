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
import org.jspecify.annotations.Nullable;
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
     * @return Environment metadata always has property expressions.
     */
    @Override
    default boolean hasPropertyExpressions() {
        return true;
    }

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
    <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

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
    <E extends Enum<E>> Optional<E> enumValue(String annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

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
    <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

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
    <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    Optional<Class> classValue(Class<? extends Annotation> annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    @Internal
    Optional<Class> classValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the int value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Internal
    OptionalInt intValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
    * Retrieve the boolean value and optionally map its value.
    * @param annotation The annotation
    * @param member The member
    * @param valueMapper The value mapper
    * @return The boolean value
    */
    Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    Optional<Boolean> booleanValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the long value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @Internal
    OptionalLong longValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the long value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    OptionalLong longValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the int value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    OptionalInt intValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    Optional<String> stringValue(Class<? extends Annotation> annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    String [] stringValues(Class<? extends Annotation> annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    String [] stringValues(String annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The string value
     */
    Optional<String> stringValue(String annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    boolean isTrue(Class<? extends Annotation> annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    boolean isTrue(String annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the double value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Internal
    OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the double value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Internal
    OptionalDouble doubleValue(String annotation, String member, Function<Object, Object> valueMapper);

    /**
     * Resolves the given value performing type conversion as necessary.
     * @param annotation The annotation
     * @param member The member
     * @param requiredType The required type
     * @param valueMapper The value mapper
     * @param <T> The generic type
     * @return The resolved value
     */
    <T> Optional<T> getValue(String annotation, String member, Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper);
}
