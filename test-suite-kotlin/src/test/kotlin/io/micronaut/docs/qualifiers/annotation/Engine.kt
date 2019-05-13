package io.micronaut.docs.qualifiers.annotation

// tag::class[]
interface Engine { // <1>
    val cylinders : Int
    fun start(): String
}
// end::class[]