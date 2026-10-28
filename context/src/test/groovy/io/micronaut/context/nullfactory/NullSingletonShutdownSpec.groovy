package io.micronaut.context.nullfactory

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class NullSingletonShutdownSpec extends Specification {

    void "stopping a context destroys every singleton a nullable factory produced as null, releasing what each resolved"() {
        given:
        def context = ApplicationContext.run(["spec.name": "NullSingletonShutdownSpec"])
        NullProductFactory.Resource.RELEASED.set(0)

        expect: "both factory methods produced no bean"
        !context.findBean(NullProductFactory.Product, Qualifiers.byName("first")).isPresent()
        !context.findBean(NullProductFactory.Product, Qualifiers.byName("second")).isPresent()

        when:
        context.stop()

        then: "the stop completes, and each registration released the resource its factory method resolved, once"
        noExceptionThrown()
        NullProductFactory.Resource.RELEASED.get() == 2
    }
}
