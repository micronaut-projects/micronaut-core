package io.micronaut.inject.lifecycle.beancreationlambda

import io.micronaut.context.BeanContext
import spock.lang.Specification

class BeanCreationEventListenerAsLambdaSpec extends Specification {


    void "test bean creation listener"() {
        given:
        BeanContext context = BeanContext.run()

        when:
        B b= context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

    }
}
