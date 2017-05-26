package org.particleframework.inject.qualifiers

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.annotation.Primary
import org.particleframework.context.annotation.Replaces
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class ReplacesSpec extends Specification {

    void "test that a bean can be marked to replace another bean"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:"A bean has a dependency on an interface with multiple impls"
        B b = context.getBean(B)

        then:"The impl that replaces the other impl is the only one present"
        b.all.size() == 1
        !b.all.any() { it instanceof A1 }
        b.all.any() { it instanceof A2 }
        b.a instanceof A2
    }

    static interface A {}


    @Singleton
    static class A1 implements A {}

    @Replaces(A1)
    @Singleton
    static class A2 implements A {}

    static class B {
        @Inject
        List<A> all

        @Inject
        A a
    }
}
