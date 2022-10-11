package io.micronaut.docs.factories.primitive;

// tag::imports[]
import jakarta.inject.Named;
import jakarta.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class V8Engine {
    private final int cylinders;

    public V8Engine(@Named("V8") int cylinders) { // <1>
        this.cylinders = cylinders;
    }

    public int getCylinders() {
        return cylinders;
    }
}
// end::class[]