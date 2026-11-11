package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "NamedReceiverGraphSpec")
public class NamedProductFactory {
    @Bean
    @Singleton
    @Named("x")
    NamedProduct namedProduct(NamedRepo repo) {
        return new NamedProduct(repo);
    }

    @Bean
    @Singleton
    @Any
    AnyProduct anyProduct(NamedRepo repo) {
        return new AnyProduct(repo);
    }
}
