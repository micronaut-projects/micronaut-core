package io.micronaut.docs.lifecycle

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext

class VehicleSpec: StringSpec() {

    init {
        "test start vehicle" {
            // tag::start[]
            val context = BeanContext.run()
            val vehicle = context
                    .getBean(Vehicle::class.java)

            println(vehicle.start())
            // end::start[]

            vehicle.engine.javaClass shouldBe V8Engine::class.java
            (vehicle.engine as V8Engine).isIntialized shouldBe true

            context.close()
        }
    }
}
