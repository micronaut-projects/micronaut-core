package io.micronaut.docs.config.properties

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.run(
                ['my.engine.cylinders': '8'],
                "test"
        )

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Ford Engine Starting V8 [rodLength=6.0]"

        cleanup:
        applicationContext.close()
    }
}
