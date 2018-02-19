package org.particleframework.inject.constructor

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject

class ConstructorNullableSpec extends Specification {


    void "test nullable injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is not injected, but null is"
        b.a == null
    }

    void "test normal injection still fails"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        C c =  context.getBean(C)

        then:"The bean is not found"
        thrown(DependencyInjectionException)
    }

    static interface A {

    }

    static class B {
        private A a

        @Inject
        B(@Nullable A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

    static class C {
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
