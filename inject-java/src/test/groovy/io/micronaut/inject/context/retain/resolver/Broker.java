package io.micronaut.inject.context.retain.resolver;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A singleton the connection resolves through its resolver and shares with others: it outlives the connection.
 */
@Singleton
@Requires(property = "spec.name", value = "RetainedResolverSpec")
public class Broker {
}
