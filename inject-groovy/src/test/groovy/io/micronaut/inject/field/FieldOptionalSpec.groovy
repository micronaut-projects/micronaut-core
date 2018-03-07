package io.micronaut.inject.field

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 30/05/2017.
 */
class FieldOptionalSpec extends Specification {


    void "test injection of optional objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an optional field with @Inject"
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
        private Optional<A> a

        @Inject
        private Optional<C> c

        A getA() {
            return this.a.get()
        }

        Optional<C> getC() {
            return c
        }
    }

}


