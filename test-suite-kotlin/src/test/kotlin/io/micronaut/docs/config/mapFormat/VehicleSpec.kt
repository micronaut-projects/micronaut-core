package io.micronaut.docs.config.mapFormat

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext

class VehicleSpec: StringSpec({
    "test start vehicle" {
        // tag::start[]
        val subMap = mapOf(
            0 to "thermostat",
            1 to "fuel pressure"
        )
        val map = mapOf(
            "my.engine.cylinders" to "8",
            "my.engine.sensors" to subMap
        )

        val applicationContext = ApplicationContext.run(map, "test")

        val vehicle = applicationContext.getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Engine Starting V8 [sensors=2]")

        applicationContext.close()
    }
})
