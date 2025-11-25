package io.micronaut.docs.ioc.injection.nullable

import org.jspecify.annotations.Nullable
import jakarta.inject.Singleton

@Singleton
class Vehicle {
    private final Engine engine

    Vehicle(@Nullable Engine engine) { // <1>
        this.engine = engine ?: Engine.create(6) // <2>
    }
    void start() {
        engine.start()
    }
}

record Engine(int cylinders) {

    static Engine create(int cylinders) {
        return new Engine(cylinders)
    }

    void start() {
        println("Vrooom! $cylinders")
    }
}
