package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class SizeIntArrayValidator extends AbstractSizeValidator<int[]> {
    @Override
    protected int getSize(@Nonnull int[] value) {
        return value.length;
    }
}
