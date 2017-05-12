package org.particleframework.inject.failures

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class FieldDependencyMissingFailureSpec extends Specification {


    void "test injection via setter with interface"() {
        given:
        Context context = new DefaultContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(DependencyInjectionException)
        e.message == 'Failed to inject value for field [a] of class: org.particleframework.inject.failures.FieldDependencyMissingFailureSpec$B'
    }

    static interface A {

    }

    static class B {
        @Inject
        private A a

        A getA() {
            return this.a
        }
    }

}

