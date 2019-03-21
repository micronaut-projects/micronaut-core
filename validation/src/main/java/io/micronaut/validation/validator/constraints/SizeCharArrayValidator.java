package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for char arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeCharArrayValidator extends AbstractSizeValidator<char[]> {
    @Override
    protected int getSize(@Nonnull char[] value) {
        return value.length;
    }
}
