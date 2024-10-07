package io.micronaut.inject.visitor.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

class BeanIntrospectorSpec extends Specification {

    void "test getIntrospection"() {
        when:
        BeanIntrospection<TestBean> beanIntrospection = BeanIntrospector.SHARED.getIntrospection(TestBean)

        then:
        beanIntrospection
        beanIntrospection.instantiate() instanceof TestBean
        beanIntrospection.propertyNames.size() == 5

        and:"You don't get a unique instance per call"
        BeanIntrospection.getIntrospection(TestBean).instantiate() instanceof TestBean
        beanIntrospection.is(BeanIntrospection.getIntrospection(TestBean))

        when:
        def flagProp = beanIntrospection.getRequiredProperty("flag", boolean.class)
        def testBean = beanIntrospection.instantiate()
        flagProp.set(testBean, true)

        then:
        flagProp.get(testBean)
        testBean.flag
    }

}
