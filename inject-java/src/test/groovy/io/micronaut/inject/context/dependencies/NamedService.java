package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("y")
@Requires(property = "spec.name", value = "NamedReceiverGraphSpec")
public class NamedService {
    final NamedRepo repo;

    NamedService(NamedRepo repo) {
        this.repo = repo;
    }
}
