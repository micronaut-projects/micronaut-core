package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.math.BigDecimal;

/**
 * Validator for Digits on char sequences.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class DigitsCharSequenceValidator implements DigitsValidator<CharSequence> {
    @Override
    public BigDecimal getBigDecimal(@Nonnull CharSequence value) {
        return new BigDecimal(value.toString());
    }
}
