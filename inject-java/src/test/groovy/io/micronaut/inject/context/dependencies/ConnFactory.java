package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ConnFactory {
    @Bean
    @Singleton
    @Named("a")
    Conn a() {
        return new Conn("a");
    }

    @Bean
    @Singleton
    @Named("b")
    Conn b() {
        return new Conn("b");
    }
}
