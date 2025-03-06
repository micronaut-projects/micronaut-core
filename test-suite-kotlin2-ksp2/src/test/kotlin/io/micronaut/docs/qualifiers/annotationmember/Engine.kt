package io.micronaut.docs.qualifiers.annotationmember

// tag::class[]
interface Engine {
    val cylinders: Int
    fun start(): String
}
// end::class[]