package org.particleframework.inject.method

import org.particleframework.context.DefaultContext
import org.particleframework.context.Context
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

class SetterInjectionSpec extends Specification {


    void "test injection via setter with interface"() {
        given:
        Context context = new DefaultContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    static class B {
        private A a

        @Inject
        void setA(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }
    }

}

