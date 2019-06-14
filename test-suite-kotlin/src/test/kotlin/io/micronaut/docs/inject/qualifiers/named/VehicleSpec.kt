package io.micronaut.docs.inject.qualifiers.named

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.DefaultBeanContext

class VehicleSpec : StringSpec({

    "test start vehicle" {
        // tag::start[]
        val vehicle = DefaultBeanContext().start().getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8")
    }

})
