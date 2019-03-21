package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class SizeLongArrayValidator extends AbstractSizeValidator<long[]> {
    @Override
    protected int getSize(@Nonnull long[] value) {
        return value.length;
    }
}
