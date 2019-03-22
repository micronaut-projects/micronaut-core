package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.math.BigDecimal;

@Singleton
public class DecimalMaxNumberValidator implements DecimalMaxValidator<Number> {
    @Override
    public final int doComparison(@Nonnull Number value, @Nonnull BigDecimal bigDecimal) {
        return DecimalMinNumberValidator.compareNumber(value, bigDecimal);
    }
}
