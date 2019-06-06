package io.micronaut.docs.lifecycle

import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle {
    final Engine engine

    Vehicle(Engine engine) { // <3>
        this.engine = engine
    }

    String start() {
        engine.start()
    }
}
// end::class[]