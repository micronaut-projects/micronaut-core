package io.micronaut.docs.inject.typed

// tag::class[]
interface Engine {
    val cylinders: Int
    fun start(): String
}
// tag::class[]