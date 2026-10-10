package io.micronaut.inject.context.retain.replaced;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client that received the application's credentials and keeps them, as an MQTT client keeps its authentication
 * mechanism.
 */
@Singleton
@Requires(property = "spec.name", value = "ReplacedClassRetentionSpec")
public class AuthenticatedClient {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public final Credentials credentials;

    public AuthenticatedClient(Credentials credentials) {
        this.credentials = credentials;
        CREATED.incrementAndGet();
    }
}
