package io.micronaut.docs.config.value

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import org.codehaus.groovy.runtime.DefaultGroovyMethods

class VehicleSpec : StringSpec({

    "test start vehicle with configuration" {
        // tag::start[]
        val applicationContext = DefaultApplicationContext("test")
        val map = mapOf("my.engine.cylinders" to "8")
        applicationContext.getEnvironment().addPropertySource(PropertySource.of("test", map))
        applicationContext.start()

        val vehicle = applicationContext.getBean(Vehicle::class.java)
        DefaultGroovyMethods.println(this, vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V8 Engine")

        applicationContext.close()
    }

    "test start vehicle without configuration" {
        // tag::start[]
        val applicationContext = DefaultApplicationContext("test")
        applicationContext.start()

        val vehicle = applicationContext.getBean(Vehicle::class.java)
        DefaultGroovyMethods.println(this, vehicle.start())
        // end::start[]

        vehicle.start().shouldBe("Starting V6 Engine")

        applicationContext.close()
    }

})
