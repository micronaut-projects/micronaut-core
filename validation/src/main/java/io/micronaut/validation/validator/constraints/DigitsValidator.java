package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * Abstract {@link Digits} validator implementation.
 * @param <T> The target type
 *
 * @author graemerocher
 * @since 1.2
 */
@FunctionalInterface
public interface DigitsValidator<T> extends ConstraintValidator<Digits, T> {
    @Override
    default boolean isValid(@Nullable T value, @Nonnull AnnotationValue<Digits> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            // null valid according to spec
            return true;
        }
        final int intMax = annotationMetadata.get("integer", int.class).orElse(0);
        final int fracMax = annotationMetadata.get("fraction", int.class).orElse(0);

        if (intMax < 0) {
            throw new IllegalArgumentException("The length of the integer part cannot be negative.");
        }

        if (fracMax < 0) {
            throw new IllegalArgumentException("The length of the fraction part cannot be negative.");
        }

        BigDecimal bigDecimal;
        try {
            bigDecimal = getBigDecimal(value);
        } catch (NumberFormatException e) {
            return false;
        }

        int intLen = bigDecimal.precision() - bigDecimal.scale();
        int fracLen = bigDecimal.scale() < 0 ? 0 : bigDecimal.scale();

        return intMax >= intLen && fracMax >= fracLen;
    }

    /**
     * Resolve a big decimal for the given value.
     * @param value The value
     * @return The big decimal
     */
    BigDecimal getBigDecimal(@Nonnull T value);
}
