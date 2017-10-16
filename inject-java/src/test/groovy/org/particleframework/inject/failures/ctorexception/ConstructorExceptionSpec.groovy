package org.particleframework.inject.failures.ctorexception

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

class ConstructorExceptionSpec extends Specification {

    void "test error message when exception occurs in constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(DependencyInjectionException)
        //e.cause.message == 'bad'
        e.message == '''\
Failed to inject value for parameter [c] of class: org.particleframework.inject.failures.ctorexception.A

Path Taken: B.a --> new A([C c])'''
    }

}
