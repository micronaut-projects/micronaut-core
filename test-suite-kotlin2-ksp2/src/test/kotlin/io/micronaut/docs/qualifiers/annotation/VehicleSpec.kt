package io.micronaut.docs.qualifiers.annotation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class VehicleSpec : StringSpec({

    "test vehicle start uses v8" {
        // tag::start[]
        val context = ApplicationContext.run()
        val vehicle = context.getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8")

        context.close()
    }
})
