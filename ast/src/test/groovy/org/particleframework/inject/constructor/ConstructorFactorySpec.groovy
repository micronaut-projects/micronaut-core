package org.particleframework.inject.constructor

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.scope.Provided
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConstructorFactorySpec extends Specification {

    void "test injection with constructor supplied by a provider"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a constructor that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
        b.a.c != null
        b.a.c2 != null
        b.a.d != null
        b.a.is(context.getBean(AImpl))
    }

    static interface A {

    }

    static interface C {

    }

    @Singleton
    static class D {}

    @Singleton
    static class CImpl implements C {

    }

    @Provided
    static class AImpl implements A {
        final C c
        final C c2
        @Inject protected D d

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
