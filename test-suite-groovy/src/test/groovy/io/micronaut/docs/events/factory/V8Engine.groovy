package io.micronaut.docs.events.factory

// tag::class[]
class V8Engine implements Engine {
    final int cylinders = 8
    double rodLength // <1>

    String start() {
        return "Starting V${cylinders} [rodLength=$rodLength]"
    }
}
// end::class[]