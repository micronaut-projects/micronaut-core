package io.micronaut.docs.config.properties

// tag::class[]
interface Engine { // <1>
    int getCylinders()
    String start()
}
// end::class[]