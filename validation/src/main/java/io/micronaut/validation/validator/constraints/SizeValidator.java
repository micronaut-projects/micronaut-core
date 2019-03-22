package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.Size;

/**
 * Abstract implementation of a {@link Size} validator.
 * @param <T> The type to constrain
 *
 * @author graemerocher
 * @since 1.2
 */
@FunctionalInterface
public interface SizeValidator<T> extends ConstraintValidator<Size, T> {
    @Override
    default boolean isValid(@Nullable T value, @Nonnull AnnotationValue<Size> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null considered valid according to spec
        }
        final int len = getSize(value);
        final int max = annotationMetadata.get("max", Integer.class).orElse(Integer.MAX_VALUE);
        final int min = annotationMetadata.get("min", Integer.class).orElse(0);
        return len <= max && len >= min;
    }

    /**
     * Evaluate the size for the given value.
     * @param value The value
     * @return The size
     */
    int getSize(@Nonnull T value);
}
