package io.micronaut.inject.annotation


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

import java.lang.annotation.Native

class SourceRetentionSpec extends AbstractTypeElementSpec {

    void "test source retention annotations are not retained"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''
package test;

@jakarta.inject.Singleton
class Test {
    
    
    @jakarta.inject.Inject
    @java.lang.annotation.Native
    String someField;
    
}
''')

        expect:"source retention annotations are not retained at runtime"
        !definition.injectedFields.first().annotationMetadata.hasAnnotation(Native)
    }
}
