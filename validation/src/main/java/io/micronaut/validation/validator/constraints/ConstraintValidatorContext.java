package io.micronaut.validation.validator.constraints;

import javax.annotation.Nonnull;
import javax.validation.ClockProvider;
import java.time.Clock;

/**
 * Subset of the {@link javax.validation.ConstraintValidatorContext} interface without the unnecessary parts.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ConstraintValidatorContext {

    /**
     * Returns the provider for obtaining the current time in the form of a {@link Clock},
     * e.g. when validating the {@code Future} and {@code Past} constraints.
     *
     * @return the provider for obtaining the current time, never {@code null}. If no
     * specific provider has been configured during bootstrap, a default implementation using
     * the current system time and the current default time zone as returned by
     * {@link Clock#systemDefaultZone()} will be returned.
     *
     * @since 2.0
     */
    @Nonnull ClockProvider getClockProvider();
}
