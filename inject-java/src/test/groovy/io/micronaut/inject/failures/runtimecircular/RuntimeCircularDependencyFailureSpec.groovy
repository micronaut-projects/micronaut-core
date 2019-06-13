package io.micronaut.inject.failures.runtimecircular

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.CircularDependencyException
import spock.lang.Specification

class RuntimeCircularDependencyFailureSpec extends Specification {
    void "test runtime circular dependency failure"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when: "B bean is obtained"
        B b = context.getBean(B)

        then:
        def e = thrown(CircularDependencyException)
        e != null
    }
}
