package io.micronaut.docs.qualifiers.annotationmember

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class VehicleSpec extends Specification {
    void "test start vehicle"() {
        given:
        // tag::start[]
        final ApplicationContext context = ApplicationContext.run()
        Vehicle vehicle = context.getBean(Vehicle.class)
        System.out.println(vehicle.start())
        // end::start[]

        expect:
        vehicle.start() == "Starting V8"

        cleanup:
        context.close()
    }
}
