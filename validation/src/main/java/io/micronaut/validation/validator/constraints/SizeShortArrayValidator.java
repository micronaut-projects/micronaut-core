package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for short arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeShortArrayValidator extends AbstractSizeValidator<short[]> {
    @Override
    protected int getSize(@Nonnull short[] value) {
        return value.length;
    }
}
