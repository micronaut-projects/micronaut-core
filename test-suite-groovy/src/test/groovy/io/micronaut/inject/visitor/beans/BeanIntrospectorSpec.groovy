package io.micronaut.inject.visitor.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

class BeanIntrospectorSpec extends Specification {

    void "test getIntrospection"() {
        given:
        BeanIntrospection<TestBean> beanIntrospection = BeanIntrospector.SHARED.getIntrospection(TestBean)

        expect:
        beanIntrospection
        beanIntrospection.instantiate() instanceof TestBean
        beanIntrospection.propertyNames.size() == 4

        and:"You get a unique instance per call"
        BeanIntrospection.getIntrospection(TestBean).instantiate() instanceof TestBean
        !beanIntrospection.is(BeanIntrospection.getIntrospection(TestBean))
    }

}
