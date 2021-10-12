package io.micronaut.docs.qualifiers.annotationmember;

import jakarta.inject.Singleton;

// tag::class[]
@Singleton
@Cylinders(value = 8, description = "8-cylinder V8 engine") // <1>
public class V8Engine implements Engine { // <2>
    @Override
    public int getCylinders() {
        return 8;
    }

    @Override
    public String start() {
        return "Starting V8";
    }
}
// end::class[]
