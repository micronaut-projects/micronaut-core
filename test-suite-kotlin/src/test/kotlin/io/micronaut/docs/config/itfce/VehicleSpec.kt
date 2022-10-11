package io.micronaut.docs.config.itfce

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException

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

        applicationContext.close()
    }

    "test start vehicle - invalid" {
        // tag::start[]
        val map = mapOf(
                "my.engine.cylinders" to "-10",
                "my.engine.crank-shaft.rod-length" to "7.0"
        )
        val applicationContext = ApplicationContext.run(map)
        val exception = shouldThrow<BeanInstantiationException> {
            applicationContext.getBean(Vehicle::class.java)
        }
        exception.message.shouldContain("EngineConfig.getCylinders - must be greater than or equal to 1")
        applicationContext.close()
    }
})
