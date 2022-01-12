package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class MutatedAnnotationMetadataSpec extends AbstractBeanDefinitionSpec {

    void "test mutated metadata from a visitor is available on beans"() {
        given:
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

        expect:
        definition.findMethod("receive", String).get().hasAnnotation('my.custom.Annotation')
    }
}
