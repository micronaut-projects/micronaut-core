package io.micronaut.docs.ioc.injection.field

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
internal class Vehicle {
    @Inject
    var engine: Engine? = null // <1>

    fun start() {
        engine!!.start()
    }
}

@Singleton
class Engine {
    fun start() {
        println("Vrooom!")
    }
}
