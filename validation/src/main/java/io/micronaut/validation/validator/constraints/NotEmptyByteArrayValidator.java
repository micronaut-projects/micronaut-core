package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

/**
 * Validates a byte[] is not empty.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyByteArrayValidator implements ConstraintValidator<NotEmpty, byte[]> {
    @Nonnull
    @Override
    public final Class<NotEmpty> getAnnotationType() {
        return NotEmpty.class;
    }

    @Override
    public boolean isValid(@Nullable byte[] value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null && value.length > 0;
    }
}

