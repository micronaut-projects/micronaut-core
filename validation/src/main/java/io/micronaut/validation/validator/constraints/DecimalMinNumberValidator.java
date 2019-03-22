package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.Internal;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.math.BigInteger;

@Singleton
public class DecimalMinNumberValidator implements DecimalMinValidator<Number> {
    @Override
    public int doComparison(@Nonnull Number value, @Nonnull BigDecimal bigDecimal) {
        return compareNumber(value, bigDecimal);
    }

    /**
     * Performs the comparision for number.
     * @param value The value
     * @param bigDecimal The big decimal
     * @return The result
     */
    @Internal
    static int compareNumber(@Nonnull Number value, @Nonnull BigDecimal bigDecimal) {
        int result;
        if (value instanceof BigDecimal) {
            result = ((BigDecimal) value).compareTo(bigDecimal);
        } else if (value instanceof BigInteger) {
            result = new BigDecimal((BigInteger) value).compareTo(bigDecimal);
        } else {
            result = BigDecimal.valueOf(value.doubleValue()).compareTo(bigDecimal);
        }
        return result;
    }
}
