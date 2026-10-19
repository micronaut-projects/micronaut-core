package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class PrototypeHolder {
    final PrototypeBean prototype;

    PrototypeHolder(PrototypeBean prototype) {
        this.prototype = prototype;
    }
}
