package io.micronaut.inject.context.dependencies;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ProviderService {
    final BeanProvider<Repo> repo;

    @Inject
    Provider<Handler> handlers;

    BeanProvider<Handler> aHandler;

    ProviderService(BeanProvider<Repo> repo) {
        this.repo = repo;
    }

    @Inject
    void setAHandler(@Named("a") BeanProvider<Handler> aHandler) {
        this.aHandler = aHandler;
    }

    Repo repo() {
        return repo.get();
    }
}
