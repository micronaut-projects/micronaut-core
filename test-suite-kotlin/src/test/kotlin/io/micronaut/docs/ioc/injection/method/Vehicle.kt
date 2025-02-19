package io.micronaut.docs.ioc.injection.method

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
internal class Vehicle {
    private var engine: Engine? = null

    @Inject
    fun initialize(engine: Engine) {
        this.engine = engine
    }

    fun start() {
        engine!!.start()
    }
}

@Singleton
internal class Engine {
    fun start() {
        println("Vrooom!")
    }
}
