package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class SizeDoubleArrayValidator extends AbstractSizeValidator<double[]> {
    @Override
    protected int getSize(@Nonnull double[] value) {
        return value.length;
    }
}
