package io.micronaut.docs.inject.qualifiers.named

// tag::class[]
interface Engine { // <1>
    val cylinders: Int
    fun start(): String
}
// tag::class[]
