package io.micronaut.docs.lifecycle

import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle internal constructor(internal val engine: Engine)// <3>
{

    fun start(): String {
        return engine.start()
    }
}
// end::class[]