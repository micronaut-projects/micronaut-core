package io.micronaut.docs.config.builder

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext

internal class VehicleSpec : StringSpec({

    "test start vehicle" {
        // tag::start[]
        val applicationContext = ApplicationContext.run(
                mapOf(
                        "my.engine.cylinders" to "4",
                        "my.engine.manufacturer" to "Subaru",
                        "my.engine.crank-shaft.rod-length" to 4,
                        "my.engine.spark-plug.name" to "6619 LFR6AIX",
                        "my.engine.spark-plug.type" to "Iridium",
                        "my.engine.spark-plug.company" to "NGK"
                ),
                "test"
        )

        val vehicle = applicationContext.getBean(Vehicle::class.java)
        println(vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Subaru Engine Starting V4 [rodLength=4.0, sparkPlug=Iridium(NGK 6619 LFR6AIX)]")
        applicationContext.close()
    }
})
