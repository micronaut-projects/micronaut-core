package org.particleframework.inject.failures

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject

/**
 * Created by graemerocher on 12/05/2017.
 */
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
Failed to inject value for parameter [a] of class: org.particleframework.inject.failures.ConstructorDependencyFailureSpec$B

Path Taken: new B([A a])'''
    }

    static interface A {

    }

    static class B {
        private final A a

        @Inject
        B(A a) {
            this.a = a
        }
    }

}