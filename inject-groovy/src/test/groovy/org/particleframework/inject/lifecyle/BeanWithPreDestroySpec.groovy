package org.particleframework.inject.lifecyle

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class BeanWithPreDestroySpec extends Specification{

    void "test that a bean with a pre-destroy hook works"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        !b.noArgsDestroyCalled
        !b.injectedDestroyCalled

        when:
        context.destroyBean(B)

        then:
        b.noArgsDestroyCalled
        b.injectedDestroyCalled
    }

    void "test that a bean with a pre-destroy hook works closed on close"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        !b.noArgsDestroyCalled
        !b.injectedDestroyCalled

        when:
        context.close()

        then:
        b.noArgsDestroyCalled
        b.injectedDestroyCalled
    }


    @Singleton
    static class C {

    }
    @Singleton
    static class A {

    }

    @Singleton
    static class B implements Closeable{

        boolean noArgsDestroyCalled = false
        boolean injectedDestroyCalled = false

        @Inject protected A another
        private A a

        @Inject
        void setA(A a ) {
            this.a = a
        }

        A getA() {
            return a
        }

        @PreDestroy
        void close() {
            noArgsDestroyCalled = true
        }

        @PreDestroy
        void another(C c) {
            if(c != null) {
                injectedDestroyCalled = true
            }
        }
    }
}
