package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.PositiveOrZero;

/**
 * Validates a number is positive or zero.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class PositiveOrZeroNumberValidator implements ConstraintValidator<PositiveOrZero, Number> {
    @Override
    public boolean isValid(@Nullable Number value, @Nonnull AnnotationValue<PositiveOrZero> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        // null is allowed according to spec
        return value == null || value.intValue() >= 0;
    }
}

