package io.micronaut.inject.context

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class NoPackageSpec extends AbstractBeanDefinitionSpec {

    void "test creating a bean in the default package"() {
        when:
        buildBeanDefinition('Bean', '''

import io.micronaut.context.annotation.*

@jakarta.inject.Singleton
class Bean {

}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Micronaut beans cannot be in the default package")
    }
}
