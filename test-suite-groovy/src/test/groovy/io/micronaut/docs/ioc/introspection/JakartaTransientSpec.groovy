package io.micronaut.docs.ioc.introspection

import io.micronaut.core.beans.BeanIntrospection
import org.junit.jupiter.api.Test
import spock.lang.Specification

class JakartaTransientSpec extends Specification {
    @Test
    void "test introspection with jakarta transient"() {
        when:
        BeanIntrospection<ObjectWithJakartaTransient> introspection = BeanIntrospection.getIntrospection(ObjectWithJakartaTransient)

        then:
        introspection.getProperty("tmp").isPresent()
    }
}
