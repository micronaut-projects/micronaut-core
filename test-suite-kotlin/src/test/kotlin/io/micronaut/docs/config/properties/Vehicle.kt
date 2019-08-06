package io.micronaut.docs.config.properties

import javax.inject.Singleton

@Singleton
class Vehicle(val engine: Engine)// <6>
{

    fun start(): String {
        return engine.start()
    }
}
