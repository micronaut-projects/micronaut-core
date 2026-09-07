package io.micronaut.docs.ioc.injection.nullable

import jakarta.inject.Singleton

@Singleton
internal class Vehicle(engine: Engine?) { // <1>
    val engine: Engine = engine ?: Engine.create(6) // <2>

    fun start() {
        engine.start()
    }
}

internal class Engine(val cylinders: Int) {

    companion object {
        fun create(cylinders: Int): Engine {
            return Engine(cylinders)
        }
    }

    fun start() {
        println("Vrooom! $cylinders")
    }
}
