package io.micronaut.docs.factories

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
// end::imports[]

@Property(name = "spec.name", value = "VehicleFactoriesSpec")
// tag::class[]
@MicronautTest
class VehicleMockSpec:
  @Bean
  @Replaces(classOf[Engine])
  def mockEngine: Engine = new Engine: // <1>
    override def start(): String = "Mock Started"

  @Test
  def testStartEngine(vehicle: Vehicle): Unit = // <2>
    val result = vehicle.start()
    assertEquals("Mock Started", result) // <3>
// end::class[]
