package org.particleframework.inject.failures

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 16/05/2017.
 */
class NestedDependencyFailureSpec extends Specification {

    void "test injection via setter with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(DependencyInjectionException)

        e.message == '''\
Failed to inject value for parameter [d] of class: org.particleframework.inject.failures.NestedDependencyFailureSpec$C

Message: No bean of type [org.particleframework.inject.failures.NestedDependencyFailureSpec$D] exists
Path Taken: B.a --> new A([C c]) --> new C([D d])'''
    }

    static class D {}

    @Singleton
    static class C {
        C(D d) {

        }
    }
    @Singleton
    static class A {
        A(C c) {

        }
    }

    static class B {
        @Inject
        private A a

        A getA() {
            return this.a
        }
    }

}
