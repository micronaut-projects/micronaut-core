package io.micronaut.dev.tck.fixtures;

import jakarta.inject.Singleton;

/**
 * A module registry done wrong: a static cache of a reloadable bean, which outlives the generation.
 */
@Singleton
public class StaticGreeterCache {

    public static volatile Greeter cached;

    StaticGreeterCache(Greeter greeter) {
        cached = greeter;
    }
}
