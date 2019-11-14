package io.micronaut.docs.config.immutable

import javax.inject.Singleton

@Singleton
class Vehicle(val engine: Engine)// <6>
{
    fun start(): String {
        return engine.start()
    }
}
