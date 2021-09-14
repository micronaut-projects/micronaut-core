package io.micronaut.docs.factories.primitive

// tag::imports[]
import jakarta.inject.Named
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class V8Engine {
    private final int cylinders

    V8Engine(@Named("V8") int cylinders) { // <1>
        this.cylinders = cylinders
    }

    int getCylinders() {
        return cylinders
    }
}
// end::class[]