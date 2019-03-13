package io.micronaut.docs.inject.intro

import io.micronaut.context.BeanContext
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleSpec {
    @Test
    fun testStartVehicle() {
        // tag::start[]
        val vehicle = BeanContext.run().getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        assertEquals("Starting V8", vehicle.start())
    }

}
