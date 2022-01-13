package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class AnnotationDefaultsSpec extends AbstractBeanDefinitionSpec {

    void "test annotation default values are available at compile time"() {
        when:
        def definition = buildBeanDefinition('test.TestListener', '''
package test

import io.micronaut.context.annotation.*
import jakarta.inject.Singleton
import io.micronaut.inject.visitor.SomeAnn

@Singleton
class TestListener {

    @SomeAnn
    @Executable
    void receive(String v) {
    }
}

''')

        then:
        noExceptionThrown()
    }
}
