package io.micronaut.docs.factories.primitive

// tag::imports[]
import jakarta.inject.Named
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class V8Engine(
    @param:Named("V8") val cylinders: Int // <1>
)
// end::class[]