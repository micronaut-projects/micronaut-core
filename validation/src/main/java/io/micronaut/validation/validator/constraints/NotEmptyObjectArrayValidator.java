package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.ArrayUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;

/**
 * {@link NotEmpty} validator for object arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyObjectArrayValidator implements ConstraintValidator<NotEmpty, Object[]> {

    @Nonnull
    @Override
    public Class<NotEmpty> getAnnotationType() {
        return NotEmpty.class;
    }

    @Override
    public boolean isValid(@Nullable Object[] value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return ArrayUtils.isNotEmpty(value);
    }
}
