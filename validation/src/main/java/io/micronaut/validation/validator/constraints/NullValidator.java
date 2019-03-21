package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.Null;

/**
 * Asserts and object is null.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NullValidator implements ConstraintValidator<Null, Object> {
    @Override
    public boolean isValid(@Nullable Object value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return value == null;
    }
}
