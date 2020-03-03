package io.micronaut.docs.config.mapFormat

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import org.codehaus.groovy.runtime.DefaultGroovyMethods


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
        DefaultGroovyMethods.println(this, vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Engine Starting V8 [sensors=2]")

        applicationContext.close()
    }

})
