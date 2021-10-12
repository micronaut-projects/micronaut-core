package io.micronaut.docs.inject.generics;

// tag::class[]
public interface Engine<T extends CylinderProvider> { // <1>
    default int getCylinders() {
        return getCylinderProvider().getCylinders();
    }

    default String start() {
        return "Starting " + getCylinderProvider().getClass().getSimpleName();
    }

    T getCylinderProvider();
}
// tag::class[]
