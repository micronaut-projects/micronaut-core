package io.micronaut.docs.inject.generics;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Requires(property = "spec.name", value = "VehicleGenericsSpec")
@Singleton
public class Vehicle {
    private final Engine<V8> engine;

    @Inject
    List<Engine<V6>> v6Engines;

    private Engine<V8> anotherV8;

    // tag::constructor[]
    @Inject
    public Vehicle(Engine<V8> engine) {
        this.engine = engine;
    }
    // end::constructor[]

    public String start() {
        return engine.start();
    }

    @Inject
    public void setAnotherV8(Engine<V8> anotherV8) {
        this.anotherV8 = anotherV8;
    }

    public Engine<V8> getAnotherV8() {
        return anotherV8;
    }

    public Engine<V8> getEngine() {
        return engine;
    }
}
