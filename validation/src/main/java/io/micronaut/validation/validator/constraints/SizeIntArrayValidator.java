package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * {@link javax.validation.constraints.Size} validator for int arrays.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeIntArrayValidator extends AbstractSizeValidator<int[]> {
    @Override
    protected int getSize(@Nonnull int[] value) {
        return value.length;
    }
}
