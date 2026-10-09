package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A factory whose own configuration its products are made from: their retention names no prefix of it.
 */
@Factory
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class ClientFactory {
    private final int timeout;

    public ClientFactory(ClientSettings settings) {
        this.timeout = settings.getTimeout();
    }

    @Singleton
    @Named("unnamed")
    @Retain
    Client unnamed() {
        return new Client("unnamed", timeout);
    }

    @Singleton
    @Named("other")
    @Retain(invalidatedBy = "observed.unrelated")
    Client other() {
        return new Client("other", timeout);
    }
}
