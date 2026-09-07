package io.micronaut.docs.ioc.injection.field

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle {
    @Inject lateinit var engine: Engine // <1>

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
