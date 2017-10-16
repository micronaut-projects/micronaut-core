package org.particleframework.inject.lifecyle

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class BeanWithPostConstructSpec extends Specification{

    void "test that a bean with a protected post construct hook that the hook is invoked"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        b.injectedFirst
        b.setupComplete
    }

    @Singleton
    static class A {

    }
    @Singleton
    static class B {

        boolean setupComplete = false
        boolean injectedFirst = false

        @Inject protected A another
        private A a

        @Inject
        void setA(A a ) {
            this.a = a
        }

        A getA() {
            return a
        }

        @PostConstruct
        void setup() {
            if(a != null && another != null) {
                injectedFirst = true
            }
            setupComplete = true
        }
    }
}
