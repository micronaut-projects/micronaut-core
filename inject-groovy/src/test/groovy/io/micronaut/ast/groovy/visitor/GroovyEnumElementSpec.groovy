package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery

class GroovyEnumElementSpec extends AbstractBeanDefinitionSpec {

    void "test is enum"() {
        given:
        def element = buildClassElement("""
package test

enum MyEnum {
    A, B
}
""")
        expect:
        element.isEnum()
        element.getPrimaryConstructor().get().getDeclaringType().isEnum()
    }

    void "test inner enum is enum"() {
        given:
        def element = buildClassElement("test.Foo","""
package test

class Foo {

    enum MyEnum {
        A, B
    }
}
""")
        expect:
        element.getEnclosedElement(ElementQuery.of(ClassElement.class)).get().getPrimaryConstructor().get().getDeclaringType().isEnum()
    }
}
