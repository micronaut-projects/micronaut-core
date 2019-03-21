package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Indexed;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.lang.annotation.Annotation;

/**
 * Constraint validator that can be used at either runtime or compilation time and
 * is capable of validation {@link javax.validation.Constraint} instances.
 *
 * @param <A> The annotation type
 * @param <T> The supported validation types
 */
@Immutable
@ThreadSafe
@Indexed(ConstraintValidator.class)
public interface ConstraintValidator<A extends Annotation, T> {

    /**
     * Implements the validation logic.
     *
     * <p>Implementations should be thread-safe and immutable.</p>
     *
     * @param value object to validate
     * @param annotationMetadata The annotation metadata
     * @param context The context object
     *
     * @return {@code false} if {@code value} does not pass the constraint
     */
    boolean isValid(
            @Nullable T value,
            @Nonnull AnnotationMetadata annotationMetadata,
            @Nonnull ConstraintValidatorContext context);

    /**
     * Implements the validation logic.
     *
     * <p>Implementations should be thread-safe and immutable.</p>
     *
     * @param value object to validate
     * @param context The context object
     *
     * @return {@code false} if {@code value} does not pass the constraint
     */
    default boolean isValid(
            @Nullable T value,
            @Nonnull ConstraintValidatorContext context) {
        return isValid(value, AnnotationMetadata.EMPTY_METADATA, context);
    }
}
