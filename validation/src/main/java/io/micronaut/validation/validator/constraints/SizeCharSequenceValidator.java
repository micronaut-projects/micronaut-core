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
public class SizeCharSequenceValidator extends AbstractSizeValidator<CharSequence> {
    @Override
    protected final int getSize(@Nonnull CharSequence value) {
        return value.length();
    }
}