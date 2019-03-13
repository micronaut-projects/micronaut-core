package io.micronaut.docs.inject.qualifiers.named

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

// tag::class[]
@Singleton
class Vehicle @Inject
constructor(@param:Named("v8") private val engine: Engine)// <4>
{

    fun start(): String {
        return engine.start()// <5>
    }
}
// end::class[]