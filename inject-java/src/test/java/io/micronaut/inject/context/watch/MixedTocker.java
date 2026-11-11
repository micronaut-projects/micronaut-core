package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Methods whose metadata does or does not prove that their annotation is processed at startup.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchRegistryProcessOnStartupSpec")
public class MixedTocker {
    @Tock
    public void only() {
        // only the annotations matter to the spec
    }

    @Tock
    @Buzz
    public void both() {
        // only the annotations matter to the spec
    }

    @TockStereotype
    public void stereotype() {
        // only the annotations matter to the spec
    }

    @Buzz
    public void plain() {
        // only the annotations matter to the spec
    }
}
