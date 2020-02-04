package io.micronaut.docs.qualifiers.annotation

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext


class VehicleSpec : StringSpec({

    "test vehicle start uses v8" {
        // tag::start[]
        val context = BeanContext.run()
        val vehicle = context.getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8")

        context.close()
    }
})