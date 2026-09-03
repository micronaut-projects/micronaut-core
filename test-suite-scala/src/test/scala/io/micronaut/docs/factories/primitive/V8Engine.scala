package io.micronaut.docs.factories.primitive

// tag::imports[]
import jakarta.inject.Named
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class V8Engine(@Named("V8") private val cylinders: Int): // <1>

  def getCylinders(): Int = cylinders
// end::class[]
