package io.micronaut.docs.qualifiers.annotationmember

import jakarta.inject.Singleton

// tag::class[]
@Singleton
@Cylinders(value = 6, description = "6-cylinder V6 engine")  // <1>
class V6Engine implements Engine { // <2>

    @Override
    int getCylinders() {
        return 6
    }

    @Override
    String start() {
        return "Starting V6"
    }
}
// end::class[]
