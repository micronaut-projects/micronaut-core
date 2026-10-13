package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class SecondListService {
    final List<Handler> handlers;

    SecondListService(List<Handler> handlers) {
        this.handlers = handlers;
    }
}
