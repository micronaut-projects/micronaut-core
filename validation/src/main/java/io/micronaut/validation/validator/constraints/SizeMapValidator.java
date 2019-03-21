package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Validates the {@link javax.validation.constraints.Size} of a map.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeMapValidator extends AbstractSizeValidator<Map> {
    @Override
    protected final int getSize(@Nonnull Map value) {
        return value.size();
    }
}
