package io.micronaut.docs.ioc.injection.optional

import io.micronaut.context.annotation.Autowired
import jakarta.inject.Singleton

@Singleton
class Vehicle {
    @Autowired(required = false)
    var engine:  Engine = Engine() // <1>

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
