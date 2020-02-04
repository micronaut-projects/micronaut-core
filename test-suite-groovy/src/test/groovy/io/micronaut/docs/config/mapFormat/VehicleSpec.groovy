package io.micronaut.docs.config.mapFormat

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        ApplicationContext applicationContext = ApplicationContext.run(
                ['my.engine.cylinders': '8', 'my.engine.sensors': [0: 'thermostat', 1: 'fuel pressure']],
                "test"
        )

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Engine Starting V8 [sensors=2]"

        cleanup:
        applicationContext.close()
    }
}
