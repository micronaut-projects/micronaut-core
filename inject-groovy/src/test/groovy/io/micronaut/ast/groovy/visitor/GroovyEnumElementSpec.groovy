package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class GroovyEnumElementSpec extends AbstractBeanDefinitionSpec {

    void "test GroovyEnumElement"() {
        given:
        def element = buildClassElement("""
package test
enum MyEnum {
    A, B
}
""")
        expect:
        element.isEnum()
    }
}
