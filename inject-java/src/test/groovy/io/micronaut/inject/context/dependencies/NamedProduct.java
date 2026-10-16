package io.micronaut.inject.context.dependencies;

public class NamedProduct {
    final NamedRepo repo;

    NamedProduct(NamedRepo repo) {
        this.repo = repo;
    }
}
