package io.micronaut.docs.events.factory

import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle {
    final Engine engine
    Vehicle(Engine engine) {
        this.engine = engine
    }

    String start() {
        engine.start()
    }
}
// end::class[]