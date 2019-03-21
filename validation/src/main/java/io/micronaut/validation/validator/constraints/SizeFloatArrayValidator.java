package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class SizeFloatArrayValidator extends AbstractSizeValidator<float[]> {
    @Override
    protected int getSize(@Nonnull float[] value) {
        return value.length;
    }
}
