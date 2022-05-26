package io.micronaut.docs.inject.generics

import jakarta.inject.Singleton

// tag::class[]
@Singleton
class V6Engine implements Engine<V6> {  // <1>
    @Override
    V6 getCylinderProvider() { new V6() }
}
// end::class[]
