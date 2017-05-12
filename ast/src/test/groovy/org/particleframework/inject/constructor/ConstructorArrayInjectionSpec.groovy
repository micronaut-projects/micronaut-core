package org.particleframework.inject.constructor

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConstructorArrayInjectionSpec extends Specification {


    void "test injection with constructor"() {
        given:
        Context context = new DefaultContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
        b.all.contains(context.getBean(AnotherImpl))
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
        private A[] all

        @Inject
        B(A[] all) {
            this.all = all
        }

        List<A> getAll() {
            return all.toList()
        }
    }

}



