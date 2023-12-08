package io.micronaut.docs.lifecycle

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class VehicleSpec extends Specification {

    void "test start vehicle"() {

        when:
        // tag::start[]
        ApplicationContext context = ApplicationContext.run()
        Vehicle vehicle = context.getBean(Vehicle)
        println vehicle.start()
        // end::start[]

        then:
        vehicle.engine instanceof V8Engine
        ((V8Engine)vehicle.engine).initialized

        cleanup:
        context.close()
    }
}
