package io.micronaut.docs.inject.intro

import io.micronaut.context.BeanContext
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class VehicleSpec {
  @Test def testStartVehicle(): Unit = {
    // tag::start[]
    val context = BeanContext.run
    val vehicle = context.getBean(classOf[Vehicle])
    System.out.println(vehicle.start)
    // end::start[]
    assertEquals("Starting V8", vehicle.start)
    context.close()
  }
}
