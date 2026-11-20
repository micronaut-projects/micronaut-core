package io.micronaut.inject.context.retain.received;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A listener that configuration receives, which a bean made from that configuration may hold.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationReceivedSpec")
public class SettingsListener {
}
