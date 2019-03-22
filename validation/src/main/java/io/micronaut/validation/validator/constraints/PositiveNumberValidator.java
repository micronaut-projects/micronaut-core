package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.Positive;

/**
 * Validates a number of a positive value.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class PositiveNumberValidator implements ConstraintValidator<Positive, Number> {
    @Override
    public boolean isValid(@Nullable Number value, @Nonnull AnnotationValue<Positive> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        // null is allowed according to spec
        return value == null || value.intValue() > 0;
    }
}
