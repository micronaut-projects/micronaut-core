package io.micronaut.docs.events.factory

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import org.junit.Test

import org.junit.Assert.assertEquals

class VehicleSpec : StringSpec({

    "test start vehicle" {
        // tag::start[]
        val context = BeanContext.run()
        val vehicle = context
                .getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8 [rodLength=6.6]")
        context.close()
    }

})
