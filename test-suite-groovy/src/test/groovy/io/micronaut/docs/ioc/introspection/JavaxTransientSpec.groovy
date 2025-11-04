package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification

class JavaxTransientSpec extends Specification {
    void "test introspection with javax transient"() {
        when:
        BeanIntrospection<ObjectWithJavaxTransient> introspection = BeanIntrospection.getIntrospection(ObjectWithJavaxTransient)

        then:
        introspection.getProperty("tmp").isPresent()
    }
}
