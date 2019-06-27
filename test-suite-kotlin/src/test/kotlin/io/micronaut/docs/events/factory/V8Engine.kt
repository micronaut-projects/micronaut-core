package io.micronaut.docs.events.factory

// tag::class[]
class V8Engine(var rodLength: Double) : Engine {  // <1>

    override val cylinders = 8

    override fun start(): String {
        return "Starting V$cylinders [rodLength=$rodLength]"
    }
}
// end::class[]