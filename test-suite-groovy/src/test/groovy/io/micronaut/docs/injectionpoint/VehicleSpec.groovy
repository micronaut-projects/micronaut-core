package io.micronaut.docs.injectionpoint

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        ApplicationContext context = ApplicationContext.run()
        Vehicle vehicle = context.getBean(Vehicle)
        println vehicle.start()
        // end::start[]

        then:
        vehicle.start() == "Starting V6"

        cleanup:
        context.close()
    }
}
