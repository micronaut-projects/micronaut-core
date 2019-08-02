package io.micronaut.docs.config.mapFormat

import javax.inject.Singleton

@Singleton
class Vehicle(val engine: Engine) {

    fun start(): String {
        return engine.start()
    }
}
