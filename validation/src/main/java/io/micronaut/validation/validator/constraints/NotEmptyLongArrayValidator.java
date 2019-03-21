package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

@Singleton
public class NotEmptyLongArrayValidator implements ConstraintValidator<NotEmpty, long[]> {
    @Override
    public boolean isValid(@Nullable long[] value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null && value.length > 0;
    }
}

