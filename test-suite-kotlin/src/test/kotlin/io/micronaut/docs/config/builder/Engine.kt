package io.micronaut.docs.config.builder

// tag::class[]
internal interface Engine { // <1>
    val cylinders: Int
    fun start(): String
}
// end::class[]