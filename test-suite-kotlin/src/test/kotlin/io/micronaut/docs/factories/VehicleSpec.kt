package io.micronaut.docs.factories

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.DefaultBeanContext

internal class VehicleSpec : StringSpec({

    "test start vehicle" {
        // tag::start[]
        val vehicle = DefaultBeanContext()
                .start()
                .getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8")
    }
})
