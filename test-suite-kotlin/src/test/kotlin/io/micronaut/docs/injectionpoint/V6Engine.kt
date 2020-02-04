package io.micronaut.docs.injectionpoint

// tag::class[]
internal class V6Engine(private val crankShaft: CrankShaft) : Engine {
    private val cylinders = 6

    override fun start(): String {
        return "Starting V6"
    }
}
// end::class[]
