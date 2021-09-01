package io.micronaut.inject.annotation

import io.micronaut.context.annotation.Parameter
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.annotation.repeatable.Topic

class AnnotationDefaultValuesSpec extends AbstractTypeElementSpec {

    void 'test getRequiredValue from AnnotationValue'() {
        given:
        BeanDefinition definition = buildBeanDefinition('issue5048.Test','''\
package issue5048;

import io.micronaut.inject.annotation.repeatable.Topic;

@jakarta.inject.Singleton
@Topic("test")
class Test {
    
}
''')
        when:
        def annotationValue = definition.getAnnotationValuesByType(Topic)[0]
        then:
        annotationValue.getRequiredValue("qos", Integer.class) == 1

    }

    void "test write annotation default values for constructor arguments"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

@jakarta.inject.Singleton
class Test {
    Test(@io.micronaut.inject.annotation.ParamAnn Foo[] foo) {}
}

@jakarta.inject.Singleton
class Foo {}
''')
        expect:
        definition.constructor.arguments[0].annotationMetadata.getAnnotationType(
                ParamAnn.name
        ).isPresent()
    }
}
