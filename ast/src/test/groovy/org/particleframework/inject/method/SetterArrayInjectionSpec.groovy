package org.particleframework.inject.method

import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.BeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 11/05/2017.
 */
class SetterArrayInjectionSpec extends Specification {
    void "test injection via setter that takes an array"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b =  context.getBean(B)

        then:
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    @Singleton
    static class AnotherImpl implements A {

    }

    static class B {
        private List<A> all

        @Inject
        void setA(A[] a) {
            this.all = Arrays.asList(a)
        }

        List<A> getAll() {
            return this.all
        }
    }
}
