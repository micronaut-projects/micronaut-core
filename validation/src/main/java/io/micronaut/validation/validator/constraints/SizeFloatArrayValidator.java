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
public class SizeFloatArrayValidator extends AbstractSizeValidator<float[]> {
    @Override
    protected int getSize(@Nonnull float[] value) {
        return value.length;
    }
}
