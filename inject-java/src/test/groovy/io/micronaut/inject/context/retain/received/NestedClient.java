package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Singleton;

/**
 * A retained bean made from configuration that received other configuration only.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class NestedClient {
    public final int size;

    NestedClient(NestedSettings settings) {
        this.size = settings.plain().getSize();
    }
}
