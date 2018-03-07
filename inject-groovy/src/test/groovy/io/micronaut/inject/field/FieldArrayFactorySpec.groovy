package io.micronaut.inject.field

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class FieldArrayFactorySpec extends Specification {

    void "test injection with field supplied by a provider"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a field that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.all != null
        b.all[0] instanceof AImpl
        b.all[0].c != null
        b.all[0].c2 != null
        b.all[0].is(context.getBean(AImpl))
    }

    static interface A {

    }

    static interface C {

    }

    @Singleton
    static class CImpl implements C {

    }
    static class AImpl implements A {
        final C c
        final C c2

        AImpl(C c, C c2) {
            this.c = c
            this.c2 = c2
        }
    }

    @Singleton
    static class AProvider implements Provider<A> {
        final C c
        @Inject C another

        @Inject AProvider(C c) {
            this.c = c
        }

        @Override
        A get() {
            new AImpl(c, another)
        }
    }

    static class B {
        @Inject private A[] all

        A[] getAll() {
            return this.all
        }
    }

}


