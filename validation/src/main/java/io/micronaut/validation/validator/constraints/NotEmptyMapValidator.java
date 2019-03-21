package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Validates that a map is not empty.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class NotEmptyMapValidator implements ConstraintValidator<NotEmpty, Map> {

    @Nonnull
    @Override
    public final Class<NotEmpty> getAnnotationType() {
        return NotEmpty.class;
    }

    @Override
    public boolean isValid(@Nullable Map value, @Nonnull AnnotationValue<NotEmpty> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        return CollectionUtils.isNotEmpty(value);
    }
}
