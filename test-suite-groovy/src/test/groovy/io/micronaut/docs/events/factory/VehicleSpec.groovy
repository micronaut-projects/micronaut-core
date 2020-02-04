package io.micronaut.docs.events.factory

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
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
        vehicle.start() == "Starting V8 [rodLength=6.6]"

        cleanup:
        context.close()
    }
}
