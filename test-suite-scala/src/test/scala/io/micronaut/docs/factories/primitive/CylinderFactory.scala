package io.micronaut.docs.factories.primitive

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
// end::imports[]

// tag::class[]
@Factory
class CylinderFactory:
  @Bean
  @Named("V8") // <1>
  def v8: Int = 8

  @Bean
  @Named("V6") // <1>
  def v6: Int = 6
// end::class[]
