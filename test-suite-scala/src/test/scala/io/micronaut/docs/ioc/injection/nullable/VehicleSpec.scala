package io.micronaut.docs.ioc.injection.nullable

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VehicleSpec:

  @Test
  def nullableInjectionFallsBackWhenNoEngineBeanExists(): Unit =
    val context = ApplicationContext.run()
    try
      val vehicle = context.getBean(classOf[Vehicle])
      assertEquals(6, vehicle.engine.cylinders)
    finally context.close()
