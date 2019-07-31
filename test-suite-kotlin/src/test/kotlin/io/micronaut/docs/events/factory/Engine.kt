package io.micronaut.docs.events.factory

// tag::class[]
interface Engine {
    val cylinders: Int
    fun start(): String
}
// end::class[]
