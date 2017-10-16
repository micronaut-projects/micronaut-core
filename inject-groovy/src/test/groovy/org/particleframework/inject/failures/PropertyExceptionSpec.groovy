package org.particleframework.inject.failures

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.BeanInstantiationException
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class PropertyExceptionSpec extends Specification {


    void "test error message when exception occurs setting a property"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(BeanInstantiationException)
        e.cause.message == 'bad'
        e.message == '''\
Error instantiating bean of type  [org.particleframework.inject.failures.PropertyExceptionSpec$B]

Message: bad
Path Taken: B.a'''
    }

    @Singleton
    static class C {
    }
    @Singleton
    static class A {
        @Inject
        void setC(C c) {
            throw new RuntimeException("bad")
        }
    }

    static class B {
        @Inject
        private A a

        A getA() {
            return this.a
        }
    }

}
