package io.micronaut.docs.config.immutable

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext

class VehicleSpec: StringSpec({

    "test start vehicle" {
        // tag::start[]
        val map = mapOf(
                "my.engine.cylinders" to "8",
                "my.engine.crank-shaft.rod-length" to "7.0"
        )
        val applicationContext = ApplicationContext.run(map)

        val vehicle = applicationContext.getBean(Vehicle::class.java)
        // end::start[]

        vehicle.start().shouldBe("Ford Engine Starting V8 [rodLength=7.0]")
    }

})