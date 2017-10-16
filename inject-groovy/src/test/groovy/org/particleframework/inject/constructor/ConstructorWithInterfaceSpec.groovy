package org.particleframework.inject.constructor

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.annotation.Provided
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Created by graemerocher on 25/05/2017.
 */
class ConstructorWithInterfaceSpec extends Specification {

    void "test injection with constructor with an interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a constructor that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
    }

    static interface A {
    }

    @Singleton
    static class AImpl implements A {
    }

    static class B {
        private A a

        @Inject
        B(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }
    }
}
