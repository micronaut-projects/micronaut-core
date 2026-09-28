package io.micronaut.inject.context.dependencies;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ProviderService {
    final BeanProvider<Repo> repo;

    ProviderService(BeanProvider<Repo> repo) {
        this.repo = repo;
    }

    Repo repo() {
        return repo.get();
    }
}
