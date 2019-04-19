package io.micronaut.docs.config.builder

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext

/**
 * @author Will Buck
 * @since 1.1
 */
internal class VehicleSpec : StringSpec({

    "test start vehicle".config(enabled = false) {
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