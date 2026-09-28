package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ProducedFactory {
    @Bean
    @Singleton
    Produced produced(Repo repo) {
        return new Produced(repo);
    }
}
