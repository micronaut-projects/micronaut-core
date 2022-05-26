package io.micronaut.docs.inject.generics

import jakarta.inject.Singleton

// tag::class[]
@Singleton
class V6Engine : Engine<V6> { // <1>
    override val cylinderProvider: V6
        get() = V6()
}
// end::class[]
