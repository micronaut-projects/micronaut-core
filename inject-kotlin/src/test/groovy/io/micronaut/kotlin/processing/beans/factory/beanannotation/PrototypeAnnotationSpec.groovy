package io.micronaut.kotlin.processing.beans.factory.beanannotation

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class PrototypeAnnotationSpec extends Specification{

    void "test @bean annotation makes a class available as a bean"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        expect:
        beanContext.getBean(A) != beanContext.getBean(A) // prototype by default
    }
}
