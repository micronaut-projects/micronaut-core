package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

// tag::class[]
@Singleton
class V6Engine implements Engine { // <2>
    int cylinders = 6

    String start() {
        "Starting V6"
    }
}
// end::class[]
