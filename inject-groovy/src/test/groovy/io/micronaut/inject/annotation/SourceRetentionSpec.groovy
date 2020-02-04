package io.micronaut.inject.annotation

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

import java.lang.annotation.Native

class SourceRetentionSpec extends AbstractBeanDefinitionSpec {
    void "test source retention annotations are not retained"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''
package test;

@javax.inject.Singleton
class Test {
    
    
    @javax.inject.Inject
    @java.lang.annotation.Native
    protected String someField;
    
}
''')

        expect:
        !definition.injectedFields.first().annotationMetadata.hasAnnotation(Native)
    }
}
