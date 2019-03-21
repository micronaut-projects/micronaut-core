package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class SizeCharArrayValidator extends AbstractSizeValidator<char[]> {
    @Override
    protected int getSize(@Nonnull char[] value) {
        return value.length;
    }
}
