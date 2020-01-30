package io.micronaut.docs.injectionpoint

import io.micronaut.context.BeanContext
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
        BeanContext beanContext = BeanContext.run()
        def vehicle = beanContext.getBean(Vehicle)
        println( vehicle.start() )
        // end::start[]

        then:
        vehicle.start() == "Starting V6"

        cleanup:
        beanContext.close()
    }
}

