package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Carries {@link Buzz} on its methods only, so the legacy listener, which selects by the class, gives it nothing.
 */
@Singleton
@BuzzListener
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "buzz-processor.enabled", value = "true")
public class BuzzOnMethods {

    @Buzz
    public void first() {
    }

    @Buzz
    public void second() {
    }
}
