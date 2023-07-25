package io.micronaut.inject.beans.concopy

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection

class ConstructorCopySpec extends AbstractTypeElementSpec {

    void "test constructor dispatch"() {
        when:
            BeanIntrospection.getIntrospection(SubscribeMessage.class)
        then:
            noExceptionThrown()
    }

}
