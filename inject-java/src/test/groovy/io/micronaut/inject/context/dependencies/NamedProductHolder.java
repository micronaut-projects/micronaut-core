package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "NamedReceiverGraphSpec")
public class NamedProductHolder {
    final NamedProduct product;

    NamedProductHolder(@Named("x") NamedProduct product) {
        this.product = product;
    }
}
