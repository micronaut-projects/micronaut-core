package io.micronaut.docs.events.factory

import javax.inject.Singleton

@Singleton
class Vehicle(val engine: Engine) {

    fun start(): String {
        return engine.start()
    }
}
