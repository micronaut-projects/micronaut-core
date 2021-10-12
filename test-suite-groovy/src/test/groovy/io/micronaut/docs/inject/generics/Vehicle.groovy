package io.micronaut.docs.inject.generics

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle {
    private final Engine<V8> engine

    @Inject
    List<Engine<V6>> v6Engines

    private Engine<V8> anotherV8

    // tag::constructor[]
    @Inject
    Vehicle(Engine<V8> engine) {
        this.engine = engine
    }
    // end::constructor[]

    String start() {
        return engine.start()
    }

    @Inject
    void setAnotherV8(Engine<V8> anotherV8) {
        this.anotherV8 = anotherV8
    }

    Engine<V8> getAnotherV8() {
        return anotherV8
    }

    Engine<V8> getEngine() {
        return engine
    }
}
