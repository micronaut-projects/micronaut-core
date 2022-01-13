package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class AnnotationMetadataSpec extends AbstractBeanDefinitionSpec {

    void "test mutated metadata from a visitor is available on beans"() {
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
        noExceptionThrown() //asserts default values are available
        definition.findMethod("receive", String).get().hasAnnotation('my.custom.Annotation')
    }
}
