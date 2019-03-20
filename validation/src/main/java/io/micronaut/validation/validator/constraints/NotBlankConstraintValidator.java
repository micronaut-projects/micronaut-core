package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;

/**
 * Validator for the {@link NotBlank} constraint.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotBlankConstraintValidator implements ConstraintValidator<NotBlank, CharSequence> {
    @Override
    public boolean isValid(@Nullable CharSequence value, @Nonnull AnnotationMetadata element, @Nullable ConstraintValidatorContext context) {
        return StringUtils.isNotEmpty(value) && value.toString().trim().length() > 0;
    }
}
