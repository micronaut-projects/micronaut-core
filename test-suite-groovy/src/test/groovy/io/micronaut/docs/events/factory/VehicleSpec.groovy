package io.micronaut.docs.events.factory

import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        Vehicle vehicle = new DefaultBeanContext()
                .start()
                .getBean(Vehicle)
        println( vehicle.start() )
        // end::start[]

        then:
        vehicle.start() == "Starting V8 [rodLength=6.6]"
    }
}
