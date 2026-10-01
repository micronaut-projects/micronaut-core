package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Order

class PriorityAnnotationMapperSpec extends AbstractTypeElementSpec {

    private static final String PRIORITY = 'jakarta.annotation.Priority'

    void 'maps jakarta.annotation.Priority to Order preserving positive value'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

@Singleton
@Priority(10)
class Test {
}
''')

        expect:
        definition.intValue(Order).orElseThrow() == 10
    }

    void 'maps jakarta.annotation.Priority to Order preserving negative value'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

@Singleton
@Priority(-3)
class Test {
}
''')

        expect:
        definition.intValue(Order).orElseThrow() == -3
    }

    void 'keeps jakarta.annotation.Priority alongside the mapped Order'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

@Singleton
@Priority(10)
class Test {
}
''')

        expect: 'the mapper adds @Order without consuming the annotation it read'
        definition.getAnnotationNames().containsAll([PRIORITY, Order.name])

        and: 'so the priority is still readable exactly as it was written'
        definition.intValue(PRIORITY, 'value').orElseThrow() == 10
    }
}
