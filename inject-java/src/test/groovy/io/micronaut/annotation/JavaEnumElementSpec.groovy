package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ElementQuery

class JavaEnumElementSpec extends AbstractTypeElementSpec {

    void "test JavaEnumElement"() {
        given:
        def element = buildClassElement("""
package test;
enum MyEnum {
    A, B
}
""")
        expect:
        element.isEnum()
    }
}
