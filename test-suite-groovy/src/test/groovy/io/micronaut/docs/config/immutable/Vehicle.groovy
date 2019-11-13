package io.micronaut.docs.config.immutable

import javax.inject.Singleton

@Singleton
class Vehicle {
    Vehicle(Engine engine) {// <6>
        this.engine = engine
    }

    String start() {
        return engine.start()
    }

    final Engine getEngine() {
        return engine
    }

    private final Engine engine
}
