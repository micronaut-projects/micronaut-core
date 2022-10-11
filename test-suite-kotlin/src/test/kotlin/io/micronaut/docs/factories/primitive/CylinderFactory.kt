package io.micronaut.docs.factories.primitive

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
// end::imports[]

// tag::class[]
@Factory
class CylinderFactory {
    @get:Bean
    @get:Named("V8") // <1>
    val v8 = 8

    @get:Bean
    @get:Named("V6") // <1>
    val v6 = 6
}
// end::class[]