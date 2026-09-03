package io.micronaut.docs.ioc.injection.field

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class VehicleSpec:

  @Test
  def fieldInjectionInjectsEngine(): Unit =
    val context = ApplicationContext.run()
    try
      val vehicle = context.getBean(classOf[Vehicle])
      assertNotNull(vehicle.engine)
      vehicle.start()
    finally context.close()
