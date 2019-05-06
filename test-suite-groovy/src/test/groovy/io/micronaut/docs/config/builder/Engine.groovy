package io.micronaut.docs.config.builder

// tag::class[]
interface Engine { // <1>
    int getCylinders()
    String start()
}
// end::class[]