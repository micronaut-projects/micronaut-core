package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;


@Singleton
public class SizeByteArrayValidator extends AbstractSizeValidator<byte[]> {
    @Override
    protected int getSize(@Nonnull byte[] value) {
        return value.length;
    }
}
