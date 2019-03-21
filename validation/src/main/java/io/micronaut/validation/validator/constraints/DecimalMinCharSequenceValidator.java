package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.math.BigDecimal;

@Singleton
public class DecimalMinCharSequenceValidator extends AbstractDecimalMinValidator<CharSequence> {
    @Override
    protected int doComparison(@Nonnull CharSequence value, @Nonnull BigDecimal bigDecimal) {
        return new BigDecimal(value.toString()).compareTo(bigDecimal);
    }
}
