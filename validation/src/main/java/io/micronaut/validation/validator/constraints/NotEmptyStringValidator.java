package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

/**
 * {@link NotEmpty} validator for char sequences.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyStringValidator implements ConstraintValidator<NotEmpty, CharSequence> {
    @Override
    public boolean isValid(@Nullable CharSequence value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return StringUtils.isNotEmpty(value);
    }
}
