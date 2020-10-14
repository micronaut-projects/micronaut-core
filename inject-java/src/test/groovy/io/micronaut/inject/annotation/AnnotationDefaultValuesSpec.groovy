package io.micronaut.inject.annotation

import io.micronaut.context.annotation.Parameter
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class AnnotationDefaultValuesSpec extends AbstractTypeElementSpec {

    void "test write annotation default values for constructor arguments"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

@javax.inject.Singleton
class Test {
    Test(@io.micronaut.inject.annotation.ParamAnn Foo[] foo) {}
}

@javax.inject.Singleton
class Foo {}
''')
        expect:
        definition.constructor.arguments[0].annotationMetadata.getAnnotationType(
                ParamAnn.name
        ).isPresent()
    }
}
