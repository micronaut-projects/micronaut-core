package io.micronaut.inject.context.dependencies.application;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "RecreateRunningApplicationSpec")
public class ResolverHolder {

    final Resolver resolver;

    public ResolverHolder(Resolver resolver) {
        this.resolver = resolver;
    }
}
