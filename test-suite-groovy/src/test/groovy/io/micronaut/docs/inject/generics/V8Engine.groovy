package io.micronaut.docs.inject.generics

import jakarta.inject.Singleton

// tag::class[]
@Singleton
class V8Engine implements Engine<V8> {  // <1>
    @Override
    V8 getCylinderProvider() { new V8() }
}
// end::class[]
