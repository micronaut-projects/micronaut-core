package io.micronaut.context

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MessageUtilsSpec extends AbstractTypeElementSpec {

    void "test bean definition name normalization"() {

        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Singleton
class Test {

}
''')

        expect:
        MessageUtils.getNormalizedTypeString(definition) == "Test"
    }
}