package io.micronaut.docs.inject.qualifiers.named

import io.micronaut.context.DefaultBeanContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VehicleSpec {
    @Test
    fun testStartVehicle() {
        // tag::start[]
        val vehicle = DefaultBeanContext().start().getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        assertEquals("Starting V8", vehicle.start())
    }

}
