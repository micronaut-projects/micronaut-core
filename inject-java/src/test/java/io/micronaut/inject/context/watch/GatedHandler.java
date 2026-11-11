package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A bean with no watched methods whose requirement passes only once a {@link Gate} is registered.
 */
@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(beans = Gate.class)
public class GatedHandler {
    final Gate gate;

    GatedHandler(Gate gate) {
        this.gate = gate;
    }
}
