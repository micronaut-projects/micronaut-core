package io.micronaut.context

import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanIdentifier
import spock.lang.Specification

class BeanRegistrationOrderSpec extends Specification {
    void "registration order preserves legacy nullable definition and bean behavior"() {
        given:
        def definition = withDefinition ? Stub(BeanDefinition) { getOrder() >> 3 } : null

        expect:
        new BeanRegistration(BeanIdentifier.of('test'), definition, bean).order == expected

        where:
        withDefinition | bean                | expected
        false          | null                | 0
        false          | new Object()        | Ordered.LOWEST_PRECEDENCE
        false          | new OrderedBean()   | 12
        false          | new AnnotatedBean() | Ordered.LOWEST_PRECEDENCE
        true           | null                | 3
        true           | new Object()        | 3
        true           | new OrderedBean()   | 12
        true           | new AnnotatedBean() | 3
    }

    static class OrderedBean implements Ordered {
        @Override
        int getOrder() { 12 }
    }

    @Order(7)
    static class AnnotatedBean { }
}
