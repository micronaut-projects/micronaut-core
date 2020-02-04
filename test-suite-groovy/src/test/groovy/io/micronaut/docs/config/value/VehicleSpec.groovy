package io.micronaut.docs.config.value

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.PropertySource
import spock.lang.Specification

class VehicleSpec extends Specification {

    void "test start vehicle with configuration"() {
        when:
        // tag::start[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of('test',['my.engine.cylinders':'8']))
        applicationContext.start()

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Starting V8 Engine"

        cleanup:
        applicationContext.close()
    }

    void "test start vehicle without configuration"() {
        when:
        // tag::start[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        Vehicle vehicle = applicationContext
                .getBean(Vehicle)
        println(vehicle.start())
        // end::start[]

        then:
        vehicle.start() == "Starting V6 Engine"

        cleanup:
        applicationContext.close()
    }
}
