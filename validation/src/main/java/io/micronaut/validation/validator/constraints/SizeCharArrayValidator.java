package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for char arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeCharArrayValidator implements SizeValidator<char[]> {
    @Override
    public int getSize(@Nonnull char[] value) {
        return value.length;
    }
}
