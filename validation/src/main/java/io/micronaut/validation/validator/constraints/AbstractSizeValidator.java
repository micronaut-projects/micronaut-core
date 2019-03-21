package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationMetadata;

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
public abstract class AbstractSizeValidator<T> implements ConstraintValidator<Size, T> {

    @Override
    public boolean isValid(@Nullable T value, @Nonnull AnnotationMetadata annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null considered valid according to spec
        }
        final int len = getSize(value);
        final int max = annotationMetadata.getValue(Size.class, "max", Integer.class).orElse(Integer.MAX_VALUE);
        final int min = annotationMetadata.getValue(Size.class, "min", Integer.class).orElse(0);
        return len <= max && len >= min;
    }

    /**
     * Evaluate the size for the given value.
     * @param value The value
     * @return The size
     */
    protected abstract int getSize(@Nonnull T value);
}
