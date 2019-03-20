package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

@Singleton
public class NotEmptyIntArrayConstraintValidator implements ConstraintValidator<NotEmpty, int[]> {
    @Override
    public boolean isValid(@Nullable int[] value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null && value.length > 0;
    }
}
