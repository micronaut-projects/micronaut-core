package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class BHandler implements Handler {
}
