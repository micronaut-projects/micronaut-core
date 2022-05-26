package io.micronaut.docs.inject.generics

import jakarta.inject.Singleton

// tag::class[]
@Singleton
class V8Engine : Engine<V8> { // <1>
    override val cylinderProvider: V8
        get() = V8()
}
// end::class[]
