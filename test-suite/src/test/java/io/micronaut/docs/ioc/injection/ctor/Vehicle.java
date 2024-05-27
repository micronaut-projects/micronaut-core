package io.micronaut.docs.ioc.injection.ctor;

import io.micronaut.core.annotation.Creator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Vehicle {
    private final Engine engine;

    @Inject // <1>
    Vehicle(Engine engine) {
        this.engine = engine;
    }

    Vehicle() {
        this.engine = Engine.create(6);
    }

    void start() {
        engine.start();
    }
}

@Singleton
record Engine(int cylinders) {

    @Creator // <2>
    static Engine getDefault() {
        return new Engine(8);
    }

    static Engine create(int cylinders) {
        return new Engine(cylinders);
    }

    void start() {
        System.out.println("Vrooom! " + cylinders);
    }
}
