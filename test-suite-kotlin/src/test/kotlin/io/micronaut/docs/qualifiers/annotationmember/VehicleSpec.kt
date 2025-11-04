package io.micronaut.docs.qualifiers.annotationmember

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VehicleSpec {
    @Test
    fun testStartVehicle() {
        // tag::start[]
        val context = ApplicationContext.run()
        val vehicle = context.getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]
        Assertions.assertEquals("Starting V8", vehicle.start())
        context.close()
    }
}