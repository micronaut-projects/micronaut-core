package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class Facade {
    final ConstructorService service;

    Facade(ConstructorService service) {
        this.service = service;
    }
}
