package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for long arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeLongArrayValidator implements SizeValidator<long[]> {
    @Override
    public int getSize(@Nonnull long[] value) {
        return value.length;
    }
}
