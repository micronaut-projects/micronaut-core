package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;


@Singleton
public class SizeByteArrayValidator implements SizeValidator<byte[]> {
    @Override
    public int getSize(@Nonnull byte[] value) {
        return value.length;
    }
}
