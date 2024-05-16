package io.micronaut.docs.ioc.injection.optional;

import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Vehicle {
    @Autowired(required = false) // <1>
    Engine engine = new Engine();

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
