package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.math.BigDecimal;

/**
 * Digits validator for Numbers.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class DigitsNumberValidator implements DigitsValidator<Number> {

    @Override
    public BigDecimal getBigDecimal(@Nonnull Number value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }
}
