package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.validation.ValidationException;
import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Interface for a class that is a registry of contraint validator.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ConstraintValidatorRegistry {

    /**
     * Finds a constraint validator for the given type and target type.
     * @param constraintType The annotation type of the constraint.
     * @param targetType The type being validated.
     * @param <A> The annotation type
     * @param <T> The target type
     * @return The validator
     */
    @Nonnull
    <A extends Annotation, T> Optional<ConstraintValidator<A, T>> findConstraintValidator(
            @Nonnull Class<A> constraintType,
            @Nonnull Class<T> targetType);

    /**
     * Finds a constraint validator for the given type and target type.
     * @param constraintType The annotation type of the constraint.
     * @param targetType The type being validated.
     * @param <A> The annotation type
     * @param <T> The target type
     * @return The validator
     * @throws ValidationException if no validator is present
     */
    @Nonnull
    default <A extends Annotation, T> ConstraintValidator<A, T> getConstraintValidator(
            @Nonnull Class<A> constraintType,
            @Nonnull Class<T> targetType) {
        return findConstraintValidator(constraintType, targetType)
                .orElseThrow(() -> new ValidationException("No constraint validator present able to validate constraint [" + constraintType + "] on type: " + targetType));
    }

}
