package io.micronaut.inject.method

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 30/05/2017.
 */
class SetterWithNullableSpec extends Specification {


    void "test injection of nullable objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an setter with @Inject and @Nullable"
        B b =  context.getBean(B)

        then:"The implementation is not injected, but null is"
        b.a == null
    }

    void "test normal injection still fails"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an setter with @Inject"
        C c =  context.getBean(C)

        then:"The bean is not found"
        thrown(DependencyInjectionException)
    }

    static interface A {

    }

    static class B {
        A a

        @Inject
        void setA(@Nullable A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

    static class C {
        A a

        @Inject
        void setA(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

}



