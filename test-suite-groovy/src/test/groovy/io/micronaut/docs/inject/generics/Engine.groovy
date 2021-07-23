package io.micronaut.docs.inject.generics

// tag::class[]
interface Engine<T extends CylinderProvider> { // <1>
    default int getCylinders() { cylinderProvider.cylinders }

    default String start() { "Starting ${cylinderProvider.class.simpleName}" }

    T getCylinderProvider()
}
// tag::class[]
