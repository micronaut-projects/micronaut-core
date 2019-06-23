package io.micronaut.docs.lifecycle

import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.specs.StringSpec
import io.micronaut.context.DefaultBeanContext


internal class VehicleSpec : StringSpec({

    "test start vehicle" {
        // tag::start[]
        val vehicle = DefaultBeanContext().start().getBean(Vehicle::class.java)

        println(vehicle.start())
        // end::start[]

        vehicle.engine.shouldBeInstanceOf<V8Engine>()
        (vehicle.engine as V8Engine).initialized.shouldBeTrue()
    }
})
