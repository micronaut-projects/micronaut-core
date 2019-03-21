package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

/**
 * {@link NotEmpty} validator for int arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyIntArrayConstraintValidator implements ConstraintValidator<NotEmpty, int[]> {
    @Nonnull
    @Override
    public final Class<NotEmpty> getAnnotationType() {
        return NotEmpty.class;
    }

    @Override
    public boolean isValid(@Nullable int[] value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null && value.length > 0;
    }
}
