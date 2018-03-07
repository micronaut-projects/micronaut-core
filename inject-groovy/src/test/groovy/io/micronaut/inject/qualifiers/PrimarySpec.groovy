package io.micronaut.inject.qualifiers

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Primary
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Primary
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class PrimarySpec extends Specification {

    void "test the @Primary annotation influences bean selection"() {

        given:
        BeanContext context = new DefaultBeanContext().start()

        when:"A bean has a dependency on an interface with multiple impls"
        B b = context.getBean(B)

        then:"The impl marked with @Primary is selected"
        context.getBeanDefinition(A2).isPrimary()
        b.all.size() == 2
        b.all.any() { it instanceof A1 }
        b.all.any() { it instanceof A2 }
        b.a instanceof A2
    }

    static interface A {}


    @Singleton
    static class A1 implements A {}

    @Primary
    @Singleton
    static class A2 implements A {}

    static class B {
        @Inject
        List<A> all

        @Inject
        A a
    }

}
