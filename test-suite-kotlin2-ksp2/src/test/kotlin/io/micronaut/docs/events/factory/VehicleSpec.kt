package io.micronaut.docs.events.factory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class VehicleSpec : StringSpec({

    "test start vehicle" {
        // tag::start[]
        val context = ApplicationContext.run()
        val vehicle = context
                .getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8 [rodLength=6.6]")
        context.close()
    }

})
