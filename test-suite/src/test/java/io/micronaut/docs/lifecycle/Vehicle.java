package io.micronaut.docs.lifecycle;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class Vehicle {
    final Engine engine;

    Vehicle(Engine engine) { // <3>
        this.engine = engine;
    }

    public String start() {
        return engine.start();
    }
}
// end::class[]