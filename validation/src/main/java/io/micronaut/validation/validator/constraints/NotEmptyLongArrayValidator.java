package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;
/**
 * {@link NotEmpty} validator for long arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyLongArrayValidator implements ConstraintValidator<NotEmpty, long[]> {
    @Override
    public boolean isValid(@Nullable long[] value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null && value.length > 0;
    }
}

