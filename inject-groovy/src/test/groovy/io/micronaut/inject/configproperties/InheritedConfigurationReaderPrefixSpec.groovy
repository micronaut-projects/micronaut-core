package io.micronaut.inject.configproperties

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Property
import io.micronaut.inject.BeanDefinition

class InheritedConfigurationReaderPrefixSpec extends AbstractBeanDefinitionSpec {


    void "test property paths are correct"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.configproperties.MyBean', '''
package io.micronaut.inject.configproperties
;

@TestEndpoint("simple")
class MyBean  {
    String myValue
}

''')

        expect:
        beanDefinition.getInjectedMethods()[0].name == 'setMyValue'
        beanDefinition.getInjectedMethods()[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.getInjectedMethods()[0].getAnnotationMetadata().getValue(Property, "name", String).get() == 'endpoints.simple.my-value'
    }
}
