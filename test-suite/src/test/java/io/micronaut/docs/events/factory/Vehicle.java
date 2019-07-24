package io.micronaut.docs.events.factory;

import javax.inject.Singleton;

@Singleton
public class Vehicle {
    public Vehicle(Engine engine) {
        this.engine = engine;
    }

    public String start() {
        return engine.start();
    }

    public final Engine getEngine() {
        return engine;
    }

    private final Engine engine;
}
