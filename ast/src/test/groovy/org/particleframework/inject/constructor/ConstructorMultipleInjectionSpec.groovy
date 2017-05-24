package org.particleframework.inject.constructor

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConstructorMultipleInjectionSpec extends Specification {



    void "test injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject and multiple arguments"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a.is(context.getBean(A))
        b.c != null
        b.c.is(context.getBean(C))
    }

    static interface A {

    }
    static interface C {

    }
    @Singleton
    static class AImpl implements A {

    }
    @Singleton
    static class CImpl implements C {

    }

    static class B {
        private A a
        private C c

        @Inject
        B(A a, C c) {
            this.a = a
            this.c = c
        }

        A getA() {
            return this.a
        }

        C getC() {
            this.c
        }
    }

}



