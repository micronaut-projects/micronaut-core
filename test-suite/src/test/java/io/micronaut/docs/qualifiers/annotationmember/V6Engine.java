package io.micronaut.docs.qualifiers.annotationmember;

import jakarta.inject.Singleton;

// tag::class[]
@Singleton
@Cylinders(value = 6, description = "6-cylinder V6 engine")  // <1>
public class V6Engine implements Engine { // <2>

    @Override
    public int getCylinders() {
        return 6;
    }

    @Override
    public String start() {
        return "Starting V6";
    }
}
// end::class[]
