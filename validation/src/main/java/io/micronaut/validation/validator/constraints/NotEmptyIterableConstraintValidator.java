package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

/**
 * {@link NotEmpty} validator for iterables.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyIterableConstraintValidator implements ConstraintValidator<NotEmpty, Iterable> {
    @Override
    public boolean isValid(@Nullable Iterable value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value != null && value.iterator().hasNext();
    }
}
