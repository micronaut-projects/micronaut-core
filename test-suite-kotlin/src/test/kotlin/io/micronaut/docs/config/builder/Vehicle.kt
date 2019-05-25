package io.micronaut.docs.config.builder

import javax.inject.Singleton

// tag::class[]
@Singleton
internal class Vehicle(val engine: Engine) {

    fun start(): String {
        return engine.start()
    }
}
// end::class[]