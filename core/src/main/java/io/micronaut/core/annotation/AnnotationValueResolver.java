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

package io.micronaut.core.annotation;

import io.micronaut.core.value.ValueResolver;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Interface for types that resolve annotation values.
 *
 * @author graemerocher
 * @since 1.0.3
 */
public interface AnnotationValueResolver extends ValueResolver<CharSequence> {
    /**
     * Return the enum value of the given member of the given enum type.
     *
     * @param member The annotation member
     * @param enumType The required type
     * @return An {@link Optional} of the enum value
     * @param <E> The enum type
     */
    <E extends Enum> Optional<E> enumValue(@Nonnull String member, @Nonnull Class<E> enumType);

    /**
     * Return the enum value of the given member of the given enum type.
     *
     * @param enumType The required type
     * @return An {@link Optional} of the enum value
     * @param <E> The enum type
     */
    default <E extends Enum> Optional<E> enumValue(@Nonnull Class<E> enumType) {
        return enumValue(AnnotationMetadata.VALUE_MEMBER, enumType);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @return An {@link Optional} class
     */
    default Optional<Class<?>> classValue() {
        return classValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The value of the given annotation member as a Class.
     *
     * @param member The annotation member
     * @return An {@link Optional} class
     */
    Optional<Class<?>> classValue(@Nonnull String member);

    /**
     * The value of the annotation as a Class.
     *
     * @return An {@link Optional} class
     */
    @Nonnull
    default Class<?>[] classValues() {
        return classValues(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The value of the given annotation member as a Class.
     *
     * @param member The annotation member
     * @return An {@link Optional} class
     */
    @Nonnull Class<?>[] classValues(@Nonnull String member);

    /**
     * The integer value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalInt}
     */
    OptionalInt intValue(@Nonnull String member);

    /**
     * The integer value of the given member.
     *
     * @return An {@link OptionalInt}
     */
    default OptionalInt intValue() {
        return intValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The long value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalLong}
     */
    OptionalLong longValue(@Nonnull String member);

    /**
     * The integer value of the given member.
     *
     * @return An {@link OptionalLong}
     */
    default OptionalLong longValue() {
        return longValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The double value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalDouble}
     */
    OptionalDouble doubleValue(@Nonnull String member);

    /**
     * The double value of the given member.
     *
     * @return An {@link OptionalDouble}
     */
    default OptionalDouble doubleValue() {
        return doubleValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The string value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalInt}
     */
    Optional<String> stringValue(@Nonnull String member);

    /**
     * The string value of the given member.
     *
     * @return An {@link OptionalInt}
     */
    default Optional<String> stringValue() {
        return stringValue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * The boolean value of the given member.
     *
     * @param member The annotation member
     * @return An {@link Optional} boolean
     */
    Optional<Boolean> booleanValue(@Nonnull String member);

    /**
     * The Boolean value of the given member.
     *
     * @return An {@link Optional} boolean
     */
    default Optional<Boolean> booleanValue() {
        return booleanValue(AnnotationMetadata.VALUE_MEMBER);
    }


    /**
     * The string value of the given member.
     *
     * @param member The annotation member
     * @return An {@link OptionalInt}
     */
    @Nonnull String[] stringValues(@Nonnull String member);

    /**
     * The double value of the given member.
     *
     * @return An {@link OptionalInt}
     */
    default @Nonnull String[] stringValues() {
        return stringValues(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * Is the given member present.
     * @param member The member
     * @return True if it is
     */
    boolean isPresent(CharSequence member);

    /**
     * @return Is the value of the annotation true.
     */
    default boolean isTrue() {
        return isTrue(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * @param member The member
     *
     * @return Is the value of the annotation true.
     */
    boolean isTrue(String member);

    /**
     * @return Is the value of the annotation true.
     */
    default boolean isFalse() {
        return isFalse(AnnotationMetadata.VALUE_MEMBER);
    }

    /**
     * @param member The member
     *
     * @return Is the value of the annotation true.
     */
    boolean isFalse(String member);

    /**
     * The value of the given annotation member as a Class.
     *
     * @param member The annotation member
     * @param requiredType The required type
     * @return An {@link Optional} class
     * @param <T> The required type
     */
    <T> Optional<Class<? extends T>> classValue(@Nonnull String member, @Nonnull Class<T> requiredType);

    /**
     * @return The attribute values
     */
    @Nonnull
    Map<CharSequence, Object> getValues();
}
