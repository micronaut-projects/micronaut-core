package org.particleframework.inject.failures

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.BeanInstantiationException
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class PostConstructExceptionSpec extends Specification {

    void "test error message when a bean has an error in the post construct method"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(BeanInstantiationException)
        e.message == 'Error instantiating bean of type [org.particleframework.inject.failures.PostConstructExceptionSpec$B]: bad'
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
            throw new RuntimeException("bad")
        }
    }
}
