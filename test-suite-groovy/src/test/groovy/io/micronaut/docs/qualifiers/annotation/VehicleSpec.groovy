package io.micronaut.docs.qualifiers.annotation

import io.micronaut.context.BeanContext
import spock.lang.Specification


class VehicleSpec extends Specification {

    void "test start vehicle"() {
        when:
        // tag::start[]
        def context = BeanContext.run()
        Vehicle vehicle = context.getBean(Vehicle)

        println( vehicle.start() )
        // end::start[]

        then:
        vehicle.start() == "Starting V8"

        cleanup:
        context.close()
    }
}
