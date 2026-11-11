package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

/**
 * Configuration that received nothing but its values.
 */
@ConfigurationProperties("received.plain")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class PlainSettings {
    private int size = 1;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
