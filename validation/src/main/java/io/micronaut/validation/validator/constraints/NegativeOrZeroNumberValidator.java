package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NegativeOrZero;

/**
 * Validates a number is negative or zero.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NegativeOrZeroNumberValidator implements ConstraintValidator<NegativeOrZero, Number> {
    @Override
    public boolean isValid(@Nullable Number value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        // null is allowed according to spec
        return value == null || value.intValue() <= 0;
    }
}
