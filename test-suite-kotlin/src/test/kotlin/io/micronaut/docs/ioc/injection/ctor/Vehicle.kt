package io.micronaut.docs.ioc.injection.ctor

import io.micronaut.core.annotation.Creator
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle {
    private val engine: Engine

    @Inject // <1>
    constructor(engine: Engine) {
        this.engine = engine
    }

    constructor() {
        this.engine = Engine.create(6)
    }

    fun start() {
        engine.start()
    }
}

@Singleton
data class Engine(val cylinders: Int) {
    fun start() {
        println("Vrooom! $cylinders")
    }

    companion object {
        @Creator // <2>
        fun getDefault() : Engine
             = Engine(8)

        fun create(cylinders: Int): Engine {
            return Engine(cylinders)
        }
    }
}
