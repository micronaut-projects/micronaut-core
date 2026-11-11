package io.micronaut.dev;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A module's client, annotated to survive restarts, that keeps the credentials the application gives it, as an MQTT
 * client keeps an authentication mechanism the application defines.
 */
@Singleton
@Retain
@Requires(property = "spec.name", value = "RetainedReceiverTest")
public class AuthenticatedClient {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final ClientCredentials credentials;

    public AuthenticatedClient(ClientCredentials credentials) {
        this.credentials = credentials;
        CREATED.incrementAndGet();
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
