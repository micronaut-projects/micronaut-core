package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

/**
 * Validates a value is not null.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotNullValidator implements ConstraintValidator<NotNull, Object> {
    @Override
    public boolean isValid(@Nullable Object value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null;
    }
}
