package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

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

    @Nonnull
    @Override
    public final Class<NotNull> getAnnotationType() {
        return NotNull.class;
    }

    @Override
    public boolean isValid(@Nullable Object value, @Nonnull AnnotationValue<NotNull> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null;
    }
}
