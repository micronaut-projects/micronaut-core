package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;

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
    <A extends Annotation, T> ConstraintValidator<A, T> find(
            @Nonnull Class<A> constraintType,
            @Nonnull Class<T> targetType);
}
