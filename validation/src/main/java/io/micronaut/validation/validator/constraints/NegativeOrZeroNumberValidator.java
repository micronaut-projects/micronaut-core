package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

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
    @Nonnull
    @Override
    public final Class<NegativeOrZero> getAnnotationType() {
        return NegativeOrZero.class;
    }

    @Override
    public boolean isValid(@Nullable Number value, @Nonnull AnnotationValue<NegativeOrZero> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        // null is allowed according to spec
        return value == null || value.intValue() <= 0;
    }
}
