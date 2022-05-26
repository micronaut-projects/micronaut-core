package io.micronaut.docs.inject.generics

import jakarta.inject.Inject
import jakarta.inject.Singleton

// tag::constructor[]
@Singleton
class Vehicle(val engine: Engine<V8>) {
// end::constructor[]

    @Inject
    lateinit var v6Engines: List<Engine<V6>>

    @set:Inject
    lateinit var anotherV8: Engine<V8>


    fun start(): String {
        return engine.start()
    }
}
