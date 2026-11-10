package io.micronaut.inject.context.dependencies.application;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A bean the server does not receive.
 */
@Singleton
@Requires(property = "spec.name", value = "RecreateRunningApplicationSpec")
public class Resolver {
}
