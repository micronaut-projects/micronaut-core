package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.Pattern;

/**
 * Validator for the {@link Pattern} annotation.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class PatternValidator extends AbstractPatternValidator<Pattern> {

    @Override
    public boolean isValid(@Nullable CharSequence value, @Nonnull AnnotationValue<Pattern> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            // null valid according to spec
            return true;
        }
        java.util.regex.Pattern regex = getPattern(annotationMetadata, false);
        return regex.matcher(value).matches();
    }
}
