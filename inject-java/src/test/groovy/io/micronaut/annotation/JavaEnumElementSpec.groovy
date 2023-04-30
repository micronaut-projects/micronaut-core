package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement

class JavaEnumElementSpec extends AbstractTypeElementSpec {

    void "test is enum"() {
        given:
        def element = buildClassElement("""
package test;

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
        def element = buildClassElement("""
package test;

class Foo {

    enum MyEnum {
        A, B
    }
}
""")
        expect:
        element.getEnclosedElement(ElementQuery.of(ClassElement.class)).get().getPrimaryConstructor().get().getDeclaringType().isEnum()
    }

    void "get enum constant annotation"() {
        given:
        def element = (EnumElement) buildClassElement("""
package test;

enum MyEnum {
    @io.micronaut.annotation.EnumConstantAnn("C")
    A,
    B
}
""")
        expect:
        for (def enumConstant : element.elements()) {
            if (enumConstant.name != 'A') {
                continue
            }
            def enumConstantAnnotation = enumConstant.getAnnotation(EnumConstantAnn.class)
            enumConstantAnnotation != null
            enumConstantAnnotation.stringValue().get() == "C"
        }
    }

    void "test enum values"() {
        given:
        def element = (EnumElement) buildClassElement("""
package test;

enum MyEnum {
    A, B
}
""")
        expect:
        element.values().size() == 2;
    }
}
