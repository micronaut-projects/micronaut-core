package io.micronaut.docs.lifecycle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class VehicleSpec: StringSpec() {

    init {
        "test start vehicle" {
            // tag::start[]
            val context = ApplicationContext.run()
            val vehicle = context
                    .getBean(Vehicle::class.java)

            println(vehicle.start())
            // end::start[]

            vehicle.engine.javaClass shouldBe V8Engine::class.java
            (vehicle.engine as V8Engine).initialized shouldBe true

            context.close()
        }
    }
}
