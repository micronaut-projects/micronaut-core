package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Order

class PriorityAnnotationMapperSpec extends AbstractTypeElementSpec {

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
}
