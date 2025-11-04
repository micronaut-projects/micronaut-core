package io.micronaut.docs.inject.generics

// tag::class[]
interface Engine<T : CylinderProvider> { // <1>
    val cylinders: Int
        get() = cylinderProvider.cylinders

    fun start(): String {
        return "Starting ${cylinderProvider.javaClass.simpleName}"
    }

    val cylinderProvider: T
}
// end::class[]