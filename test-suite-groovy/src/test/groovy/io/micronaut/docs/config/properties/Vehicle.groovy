package io.micronaut.docs.config.properties

import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle {
    final Engine engine

    Vehicle(Engine engine) { // <6>
        this.engine = engine
    }

    String start() {
        engine.start()
    }
}
// end::class[]