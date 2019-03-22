package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for double arrays.
 *
 * @author graemerocher
 * @since 1.2
 */

@Singleton
public class SizeDoubleArrayValidator implements SizeValidator<double[]> {
    @Override
    public int getSize(@Nonnull double[] value) {
        return value.length;
    }
}
