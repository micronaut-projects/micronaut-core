package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.Collection;

/**
 * Validates the size of a collection using {@link javax.validation.constraints.Size}.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class SizeCollectionValidator extends AbstractSizeValidator<Collection> {
    @Override
    protected int getSize(@Nonnull Collection value) {
        return value.size();
    }
}
