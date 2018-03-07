package io.micronaut.inject.failures.ctordependencyfailure

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

class ConstructorDependencyFailureSpec extends Specification {

    void "test a useful exception is thrown when a dependency injection failure occurs"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean that defines a constructor dependency on a missing bean"
        B b =  context.getBean(B)

        then:"The correct error is thrown"
        def e = thrown(DependencyInjectionException)
        e.message == '''\
Failed to inject value for parameter [a] of class: io.micronaut.inject.failures.ctordependencyfailure.B

Message: No bean of type [io.micronaut.inject.failures.ctordependencyfailure.A] exists
Path Taken: new B([A a])'''
    }
}