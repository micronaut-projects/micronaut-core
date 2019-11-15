package io.micronaut.docs.config.itfce

import javax.inject.Singleton

@Singleton
class Vehicle(val engine: Engine)// <6>
{
    fun start(): String {
        return engine.start()
    }
}