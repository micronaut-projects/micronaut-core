package io.micronaut.docs.inject.intro

import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle(private val engine: Engine)// <3>
{

    fun start(): String {
        return engine.start()
    }
}
// end::class[]
