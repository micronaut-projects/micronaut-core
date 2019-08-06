package io.micronaut.docs.config.properties

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import org.codehaus.groovy.runtime.DefaultGroovyMethods

class VehicleSpec: StringSpec({

    "test start vehicle" {
        // tag::start[]
        val map = mapOf( "my.engine.cylinders" to "8")
        val applicationContext = ApplicationContext.run(map, "test")

        val vehicle = applicationContext.getBean(Vehicle::class.java)
        DefaultGroovyMethods.println(this, vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Ford Engine Starting V8 [rodLength=6.0]")
    }

})
