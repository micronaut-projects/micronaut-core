package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

@Singleton
public class NotEmptyStringConstraintValidator implements ConstraintValidator<NotEmpty, CharSequence> {
    @Override
    public boolean isValid(
            @Nullable CharSequence value,
            @Nonnull AnnotationMetadata annotationMetadata,
            @Nonnull ConstraintValidatorContext context) {
        return StringUtils.isNotEmpty(value);
    }
}
