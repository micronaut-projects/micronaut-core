package io.micronaut.docs.ioc.injection.field;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Vehicle {
    @Inject Engine engine; // <1>

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
