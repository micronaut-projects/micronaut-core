package io.micronaut.docs.injectionpoint

import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification


/**
 * @author Graeme Rocher
 * @since 1.0
 */
class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        io.micronaut.docs.factories.Vehicle vehicle = new DefaultBeanContext()
                .start()
                .getBean(io.micronaut.docs.factories.Vehicle)
        println( vehicle.start() )
        // end::start[]

        then:
        vehicle.start() == "Starting V6"
    }
}

