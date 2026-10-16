package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

/**
 * Configuration that received a listener, which it hands out.
 */
@ConfigurationProperties("received.listened")
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class ListenedSettings {
    private int timeout = 1;
    private SettingsListener listener;

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public SettingsListener listener() {
        return listener;
    }

    @Inject
    void listener(SettingsListener listener) {
        this.listener = listener;
    }
}
