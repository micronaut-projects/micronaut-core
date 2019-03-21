package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.ValidationException;
import javax.validation.constraints.DecimalMax;
import java.math.BigDecimal;

/**
 * Abstract implementation of a validator for {@link DecimalMax}.
 * @param <T> The type to constrain
 *
 * @author graemerocher
 * @since 1.2
 */
public abstract class AbstractDecimalMaxValidator<T> implements ConstraintValidator<DecimalMax, T> {

    @Nonnull
    @Override
    public Class<DecimalMax> getAnnotationType() {
        return DecimalMax.class;
    }

    @Override
    public final boolean isValid(@Nullable T value, @Nonnull AnnotationValue<DecimalMax> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            // null considered valid according to spec
            return true;
        }

        final BigDecimal bigDecimal = annotationMetadata.getValue(String.class)
                .map(s ->
                        ConversionService.SHARED.convert(s, BigDecimal.class)
                                .orElseThrow(() -> new ValidationException(s + " does not represent a valid BigDecimal format.")))
                .orElseThrow(() -> new ValidationException("null does not represent a valid BigDecimal format."));

        final boolean inclusive = annotationMetadata.get("inclusive", boolean.class).orElse(true);


        int result;
        try {
            result = doComparison(value, bigDecimal);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return inclusive ? result <= 0 : result < 0;
    }


    /**
     * Perform the comparison for the given value.
     * @param value The value
     * @param bigDecimal The big decimal
     * @return The result
     */
    protected abstract int doComparison(@Nonnull T value, @Nonnull BigDecimal bigDecimal);
}
