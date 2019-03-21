package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class SizeShortArrayValidator extends AbstractSizeValidator<short[]> {
    @Override
    protected int getSize(@Nonnull short[] value) {
        return value.length;
    }
}
