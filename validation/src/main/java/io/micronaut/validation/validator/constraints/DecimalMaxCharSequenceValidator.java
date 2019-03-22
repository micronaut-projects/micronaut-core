package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.math.BigDecimal;

/**
 * Validator for {@link javax.validation.constraints.DecimalMax} on char sequences.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class DecimalMaxCharSequenceValidator implements DecimalMaxValidator<CharSequence> {
    @Override
    public int doComparison(@Nonnull CharSequence value, @Nonnull BigDecimal bigDecimal) {
        return new BigDecimal(value.toString()).compareTo(bigDecimal);
    }
}
