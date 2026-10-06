package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Carries {@link Buzz} on the class, so the legacy listener gives its methods to a processor as it is created.
 */
@Singleton
@Buzz
@BuzzListener
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "buzz-processor.enabled", value = "true")
public class BuzzOnClass {

    public void third() {
    }
}
