package io.micronaut.docs.factories

import io.micronaut.context.BeanContext
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class VehicleSpec {

  @Test def testStartVehicle(): Unit = {
    val beanContext = BeanContext.run
    val vehicle = beanContext.getBean(classOf[Vehicle])
    System.out.println(vehicle.start())
    assertEquals("Starting V8", vehicle.start())
    beanContext.close()
  }

}
