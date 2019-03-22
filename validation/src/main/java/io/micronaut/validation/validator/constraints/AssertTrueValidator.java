package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.AssertTrue;

/**
 * Validates a value is true.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public final class AssertTrueValidator implements ConstraintValidator<AssertTrue, Boolean> {
    @Override
    public boolean isValid(
            @Nullable Boolean value,
            @Nonnull AnnotationValue<AssertTrue> annotationMetadata,
            @Nonnull ConstraintValidatorContext context) {
        return value == null || value;
    }
}
