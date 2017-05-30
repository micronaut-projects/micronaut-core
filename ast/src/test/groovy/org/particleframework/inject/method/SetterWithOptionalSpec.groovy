package org.particleframework.inject.method

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 30/05/2017.
 */
class SetterWithOptionalSpec extends Specification {


    void "test injection of optional objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an optional setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        !b.c.isPresent()
    }

    static interface A {

    }

    static interface C {}

    @Singleton
    static class AImpl implements A {

    }

    static class B {
        @Inject
        Optional<A> a

        @Inject
        Optional<C> c

        A getA() {
            return this.a.get()
        }

        Optional<C> getC() {
            return c
        }
    }

}



