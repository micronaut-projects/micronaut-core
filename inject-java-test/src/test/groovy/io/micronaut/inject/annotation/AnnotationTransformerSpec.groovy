package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class AnnotationTransformerSpec extends AbstractTypeElementSpec {

    void "test transform annotation metadata"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@io.micronaut.inject.annotation.ToTransform
@jakarta.inject.Singleton
class Test {

}
''')

        expect:"The original annotation wasn't retained"
        !definition.hasAnnotation(ToTransform)
        definition.hasAnnotation('test.ToTransformOther')
    }


    void "test transform retention level"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@io.micronaut.inject.annotation.ToTransformRetention
@jakarta.inject.Singleton
class Test {

}
''')

        expect:"The original annotation wasn't retained"
        definition.hasAnnotation(ToTransformRetention)
    }
}
