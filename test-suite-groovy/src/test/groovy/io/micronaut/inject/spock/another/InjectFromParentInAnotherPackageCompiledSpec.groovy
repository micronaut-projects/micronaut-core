package io.micronaut.inject.spock.another

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class InjectFromParentInAnotherPackageCompiledSpec extends AbstractBeanDefinitionSpec {

    void "test compile spock specification that inherits from already compiled class"() {
        given:
        def definition = buildBeanDefinition('test.MySpockSpec', '''
package test

import io.micronaut.inject.spock.other.AbstractMicronautTestSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class MySpockSpec extends AbstractMicronautTestSpec {

}
''')
        expect:
        definition != null
        definition.injectedFields.size() == 1
    }
}
