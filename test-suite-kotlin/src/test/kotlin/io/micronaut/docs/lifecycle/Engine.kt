package io.micronaut.docs.lifecycle

// tag::class[]
interface Engine { // <1>
    val cylinders: Int
    fun start(): String
}
// end::class[]