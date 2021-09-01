package io.micronaut.inject.factory.composite;

public class CompositeSomeRegistry implements SomeRegistry {
    private final SomeRegistry[] someRegistries;

    public CompositeSomeRegistry(SomeRegistry[] someRegistries) {
        this.someRegistries = someRegistries;
    }

    public SomeRegistry[] getSomeRegistries() {
        return someRegistries;
    }
}
