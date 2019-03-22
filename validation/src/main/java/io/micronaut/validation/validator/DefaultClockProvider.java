package io.micronaut.validation.validator;

import javax.inject.Singleton;
import javax.validation.ClockProvider;
import java.time.Clock;

/**
 * The default clock provider.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class DefaultClockProvider implements ClockProvider {
    @Override
    public Clock getClock() {
        return Clock.systemDefaultZone();
    }
}
