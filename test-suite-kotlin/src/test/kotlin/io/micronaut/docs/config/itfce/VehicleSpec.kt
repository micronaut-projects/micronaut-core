package io.micronaut.docs.config.itfce

import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException

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

    "test start vehicle - invalid" {
        // tag::start[]
        val map = mapOf(
                "my.engine.cylinders" to "-10",
                "my.engine.crank-shaft.rod-length" to "7.0"
        )
        val applicationContext = ApplicationContext.run(map)
        val exception = shouldThrow<DependencyInjectionException> {
            applicationContext.getBean(Vehicle::class.java)
        }
        exception.cause.shouldNotBeNull()
        exception.cause?.message.shouldContain("EngineConfig.getCylinders - must be greater than or equal to 1")

    }
})