package io.micronaut.docs.inject.intro

// tag::class[]
interface Engine {
    // <1>
    val cylinders: Int

    fun start(): String
}
// end::class[]