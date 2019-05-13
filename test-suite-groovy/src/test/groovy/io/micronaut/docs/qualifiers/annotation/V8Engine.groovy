package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

// tag::class[]
@Singleton
class V8Engine implements Engine { // <3>
    int cylinders = 8

    String start() {
        "Starting V8"
    }
}
// end::class[]