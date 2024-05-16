package io.micronaut.docs.ioc.injection.method;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Vehicle {
    private Engine engine;

    @Inject // <1>
    void initialize(Engine engine) {
        this.engine = engine;
    }

    void start() {
        engine.start();
    }
}

@Singleton
class Engine {
    void start() {
        System.out.println("Vrooom!" );
    }
}
