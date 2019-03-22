package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.AssertFalse;

/**
 * Validates a value is false.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class AssertFalseValidator implements ConstraintValidator<AssertFalse, Boolean> {
    @Override
    public boolean isValid(
            @Nullable Boolean value,
            @Nonnull AnnotationValue<AssertFalse> annotationMetadata,
            @Nonnull ConstraintValidatorContext context) {
        return value == null || !value;
    }
}
