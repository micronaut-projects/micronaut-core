package io.micronaut.inject.factory.composite;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class SomeRegistryFactory {
    @Singleton
    SomeRegistry someRegistry(SomeRegistry... otherRegistries) {
        return new CompositeSomeRegistry(otherRegistries);
    }
}
