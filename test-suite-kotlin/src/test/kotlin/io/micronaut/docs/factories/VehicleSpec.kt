package io.micronaut.docs.factories

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VehicleSpec {

    @Test
    fun testStartVehicle() {
        // tag::start[]
        val context = ApplicationContext.run()
        val vehicle = context
                .getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        assertEquals("Starting V8", vehicle.start())
        context.close()
    }
}
