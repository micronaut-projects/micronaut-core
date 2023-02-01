package io.micronaut.docs.injectionpoint

import io.micronaut.context.BeanContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


internal class VehicleSpec {

    @Test
    fun testStartVehicle() {
        // tag::start[]
        BeanContext.run().use {
            val vehicle = it.getBean(Vehicle::class.java)
            println(vehicle.start())


            Assertions.assertEquals("Starting V6", vehicle.start())
        }
        // end::start[]
    }
}
