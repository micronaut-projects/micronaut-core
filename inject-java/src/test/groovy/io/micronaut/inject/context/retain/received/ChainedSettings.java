package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received a prototype holding a prototype that holds a provider.
 */
@ConfigurationProperties("received.chained")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ChainedSettings {
    private LookupHolder holder;

    public LookupHolder holder() {
        return holder;
    }

    @Inject
    void holder(LookupHolder holder) {
        this.holder = holder;
    }
}
