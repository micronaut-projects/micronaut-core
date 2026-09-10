package io.micronaut.docs.ioc.injection.method

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle {
    private lateinit var engine: Engine

    @Inject // <1>
    fun initialize(engine: Engine) {
        this.engine = engine
    }

    fun start() {
        engine.start()
    }
}

@Singleton
class Engine {
    fun start() {
        println("Vrooom!")
    }
}
