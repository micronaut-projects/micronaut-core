package io.micronaut.docs.inject.generics

// Regression in Groovy 5 : it is no longer possible to use `cylinderProvider` in
// default methods: you have to use the getter

// tag::class[]
interface Engine<T extends CylinderProvider> { // <1>
    default int getCylinders() { getCylinderProvider().cylinders }

    default String start() { "Starting ${getCylinderProvider().class.simpleName}" }

    T getCylinderProvider()
}
// tag::class[]
