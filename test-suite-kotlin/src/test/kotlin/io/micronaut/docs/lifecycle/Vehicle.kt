package io.micronaut.docs.lifecycle

import javax.inject.Singleton

@Singleton
class Vehicle(val engine: Engine)// <3>
{

    fun start(): String {
        return engine.start()
    }
}
