package io.micronaut.docs.ioc.injection.nullable

import jakarta.inject.Singleton

@Singleton
class Vehicle(engine: Engine?) { // <1>
    private val engine = engine ?: Engine.create(6) // <2>
    fun start() {
        engine.start()
    }
}

data class Engine(val cylinders: Int) {
    fun start() {
        println("Vrooom! $cylinders")
    }

    companion object {
        fun create(cylinders: Int): Engine {
            return Engine(cylinders)
        }
    }
}
