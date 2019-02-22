package io.micronaut.inject.visitor.beans

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

import javax.inject.Singleton

class BeanIntrospectorSpec extends Specification {

    void "test getIntrospection"() {
        given:
        BeanIntrospection<TestBean> beanIntrospection = BeanIntrospector.SHARED.getIntrospection(TestBean)

        expect:
        beanIntrospection
        beanIntrospection.instantiate() instanceof TestBean
        beanIntrospection.propertyNames == ['name', 'age', 'stringArray'] as String[]

        and:"You get a unique instance per call"
        BeanIntrospection.getIntrospection(TestBean).instantiate() instanceof TestBean
        !beanIntrospection.is(BeanIntrospection.getIntrospection(TestBean))
    }

    void "test find introspections"() {
        expect:
        BeanIntrospector.SHARED.findIntrospections(Introspected).size() == 2
        BeanIntrospector.SHARED.findIntrospections(Introspected, "io.micronaut.inject.visitor.beans").size() == 2
        BeanIntrospector.SHARED.findIntrospections(Introspected, "blah").size() == 0
        BeanIntrospector.SHARED.findIntrospections(Singleton).size() == 0
    }
}
