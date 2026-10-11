package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received a singleton holding a provider.
 */
@ConfigurationProperties("received.held")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class HeldSettings {
    private ProviderHolder holder;

    public ProviderHolder holder() {
        return holder;
    }

    @Inject
    void holder(ProviderHolder holder) {
        this.holder = holder;
    }
}
