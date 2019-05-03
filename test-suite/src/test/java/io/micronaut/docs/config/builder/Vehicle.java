package io.micronaut.docs.config.builder;

import javax.inject.Inject;
import javax.inject.Singleton;

// tag::class[]
@Singleton
class Vehicle {
    final Engine engine;

    @Inject
    Vehicle(Engine engine) { // <6>
        this.engine = engine;
    }

    String start() {
        return engine.start();
    }
}
// end::class[]