package io.micronaut.docs.inject.intro;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class Vehicle {
    private final Engine engine;

    public Vehicle(Engine engine) {// <3>
        this.engine = engine;
    }

    public String start() {
        return engine.start();
    }
}
// end::class[]
