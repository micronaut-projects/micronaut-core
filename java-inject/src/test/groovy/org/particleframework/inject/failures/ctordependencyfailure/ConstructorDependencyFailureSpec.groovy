package org.particleframework.inject.failures.ctordependencyfailure

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
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
Failed to inject value for parameter [a] of class: org.particleframework.inject.failures.ctordependencyfailure.B

Message: No bean of type [org.particleframework.inject.failures.ctordependencyfailure.A] exists
Path Taken: new B([A a])'''
    }
}