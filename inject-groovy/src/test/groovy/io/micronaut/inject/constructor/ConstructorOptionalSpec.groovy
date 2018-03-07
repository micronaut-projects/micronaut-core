package io.micronaut.inject.constructor

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
class ConstructorOptionalSpec extends Specification {


    void "test injection of optional objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an optional constructor with @Inject"
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
        private Optional<A> a
        private Optional<C> c

        @Inject
        B(Optional<A> a, Optional<C> c) {
            this.a = a
            this.c = c
        }

        A getA() {
            return this.a.get()
        }

        Optional<C> getC() {
            return c
        }
    }

}


