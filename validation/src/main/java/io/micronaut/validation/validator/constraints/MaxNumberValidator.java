package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import javax.validation.constraints.Max;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Validates numbers for max value.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class MaxNumberValidator implements ConstraintValidator<Max, Number> {

    @Override
    public boolean isValid(@Nullable Number value, @Nonnull AnnotationValue<Max> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }
        final Long max = annotationMetadata.getValue(Long.class).orElseThrow(() ->
                new ValidationException("@Max annotation specified without value")
        );

        if (value instanceof BigInteger) {
            return ((BigInteger) value).compareTo(BigInteger.valueOf(max)) < 0;
        } else if (value instanceof BigDecimal) {
            return ((BigDecimal) value).compareTo(BigDecimal.valueOf(max)) < 0;
        }
        return value.longValue() < max;
    }

}
