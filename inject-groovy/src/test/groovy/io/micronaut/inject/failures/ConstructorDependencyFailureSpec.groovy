package io.micronaut.inject.failures

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
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
Failed to inject value for parameter [a] of class: io.micronaut.inject.failures.ConstructorDependencyFailureSpec$B

Message: No bean of type [io.micronaut.inject.failures.ConstructorDependencyFailureSpec$A] exists
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