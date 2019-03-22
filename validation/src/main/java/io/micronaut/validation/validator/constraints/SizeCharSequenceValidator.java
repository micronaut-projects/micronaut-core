package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * Validates the size of char sequences.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeCharSequenceValidator implements SizeValidator<CharSequence> {
    @Override
    public final int getSize(@Nonnull CharSequence value) {
        return value.length();
    }
}