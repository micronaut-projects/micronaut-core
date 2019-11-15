package io.micronaut.docs.config.itfce;

import javax.inject.Singleton;

@Singleton
public class Vehicle {
    public Vehicle(Engine engine) {// <6>
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
