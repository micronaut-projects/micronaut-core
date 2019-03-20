package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.ArrayUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

@Singleton
public class NotEmptyObjectArrayConstraintValidator implements ConstraintValidator<NotEmpty, Object[]> {
    @Override
    public boolean isValid(@Nullable Object[] value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return ArrayUtils.isNotEmpty(value);
    }
}
