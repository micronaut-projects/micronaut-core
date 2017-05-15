package org.particleframework.inject.inheritance

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 15/05/2017.
 */
class AbstractInheritanceSpec extends Specification {

    void "test values are injected for abstract parent class"() {
        given:
        Context context  = new DefaultContext()
        context.start()

        when:"A bean is retrieved that has abstract inherited values"
        B b = context.getBean(B)

        then:"The values are injected"
        b.a != null
        b.another != null
        b.a.is(b.another)
    }

    @Singleton
    static class A {

    }

    static abstract class AbstractB {
        // inject via field
        @Inject protected A a
        private A another
        // inject via method
        @Inject void setAnother(A a) {
            this.another = a
        }

        A getA() {
            return a
        }

        A getAnother() {
            return another
        }
    }

    @Singleton
    static class B extends AbstractB {

    }
}
