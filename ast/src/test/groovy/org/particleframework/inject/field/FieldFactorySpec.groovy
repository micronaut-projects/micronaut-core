package org.particleframework.inject.field

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class FieldFactorySpec extends Specification {

    void "test injection with field supplied by a provider"() {
        given:
        Context context = new DefaultContext()
        context.start()

        when:"A bean is obtained which has a field that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
        b.a.c != null
        b.a.c2 != null
        b.a.is(context.getBean(AImpl))
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
        @Inject private A a

        A getA() {
            return this.a
        }
    }

}

