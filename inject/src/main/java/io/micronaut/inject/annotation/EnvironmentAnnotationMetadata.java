package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param enumType The enum type
     * @param valueMapper The value mapper
     * @param <E> The enum type
     * @return The class value
     */
    @Internal
    <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param enumType The enum type
     * @param valueMapper The value mapper
     * @param <E> The enum type
     * @return The class value
     */
    @Internal
    <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the class value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The class value
     */
    @Internal
    Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the int value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Internal
    OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
    * Retrieve the boolean value and optionally map its value.
    * @param annotation The annotation
    * @param member The member
    * @param valueMapper The value mapper
    * @return The boolean value
    */
    Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    @Nonnull
    Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the long value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @Internal
    OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the long value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The long value
     */
    @Nonnull
    OptionalLong longValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the int value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Nonnull
    OptionalInt intValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The int value
     */
    @Nonnull
    String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the string value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The string value
     */
    @Nonnull
    Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Function<Object, Object> valueMapper);

    /**
     * Retrieve the boolean value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The boolean value
     */
    boolean isTrue(@Nonnull String annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the double value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Internal
    OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nullable Function<Object, Object> valueMapper);

    /**
     * Retrieve the double value and optionally map its value.
     * @param annotation The annotation
     * @param member The member
     * @param valueMapper The value mapper
     * @return The double value
     */
    @Nonnull
    @Internal
    OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member, Function<Object, Object> valueMapper);

    /**
     * Resolves the given value performing type conversion as necessary.
     * @param annotation The annotation
     * @param member The member
     * @param requiredType The required type
     * @param valueMapper The value mapper
     * @param <T> The generic type
     * @return The resolved value
     */
    @Nonnull
    <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType, @Nullable Function<Object, Object> valueMapper);
}
