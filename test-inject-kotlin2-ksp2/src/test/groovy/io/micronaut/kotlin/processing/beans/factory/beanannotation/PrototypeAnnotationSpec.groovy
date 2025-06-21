package io.micronaut.kotlin.processing.beans.factory.beanannotation

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PrototypeAnnotationSpec extends Specification{

    void "test @bean annotation makes a class available as a bean"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.getBean(A) != context.getBean(A) // prototype by default

        cleanup:
        context.close()
    }
}
