package io.micronaut.inject.context.dependencies;

public class AnyProduct {
    final NamedRepo repo;

    AnyProduct(NamedRepo repo) {
        this.repo = repo;
    }
}
