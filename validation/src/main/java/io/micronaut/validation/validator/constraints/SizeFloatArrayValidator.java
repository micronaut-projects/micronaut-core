package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for float arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeFloatArrayValidator implements SizeValidator<float[]> {
    @Override
    public int getSize(@Nonnull float[] value) {
        return value.length;
    }
}
