package io.micronaut.inject.provider.injectionpointowner;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class SeedFactory {

    @Singleton
    @Named("one")
    Seed one() {
        return new Seed("one");
    }

    @Singleton
    @Named("two")
    Seed two() {
        return new Seed("two");
    }
}
