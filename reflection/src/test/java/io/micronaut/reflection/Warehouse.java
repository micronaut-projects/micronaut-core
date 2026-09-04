package io.micronaut.reflection;

import jakarta.inject.Singleton;

/**
 * A plain singleton with no dependency, a type the processors never saw.
 */
@Singleton
public class Warehouse {

    private final String name = "central";

    public String getName() {
        return name;
    }
}
