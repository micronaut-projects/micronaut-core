package io.micronaut.docs.ioc.injection.nullable;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Vehicle {
    private final Engine engine;

    Vehicle(@Nullable Engine engine) { // <1>
        this.engine = engine != null ? engine : Engine.create(6); // <2>
    }
    void start() {
        engine.start();
    }

    public Engine getEngine() {
        return engine;
    }
}

record Engine(int cylinders) {

    static Engine create(int cylinders) {
        return new Engine(cylinders);
    }

    void start() {
        System.out.println("Vrooom! " + cylinders);
    }
}
