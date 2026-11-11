package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

@Prototype
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class PrototypeBean {
    final Repo repo;

    PrototypeBean(Repo repo) {
        this.repo = repo;
    }
}
