package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import spock.lang.Ignore
import spock.lang.PendingFeature

class KotlinEnumElementSpec extends AbstractKotlinCompilerSpec {

    void "test is enum"() {
        given:
        def element = buildClassElement("test.MyEnum", """
package test

enum class MyEnum {
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

    enum class MyEnum {
        A, B
    }
}
""")
        expect:
        element.getEnclosedElement(ElementQuery.of(ClassElement.class)).get().getPrimaryConstructor().get().getDeclaringType().isEnum()
    }

    @Ignore
    @PendingFeature(reason = "constantValue not available with KSP")
    void "get enum constantValue for final fields"() {
        given:
        def element = (EnumElement) buildClassElement("test.MyEnum", """
package test

enum class MyEnum(
    value: Int
) {

    ENUM_VAL1(10),
    ENUM_VAL2(11),
    UNRECOGNIZED(-1),
    ;

    companion object {
        const val ENUM_VAL1_VALUE = 10
        const val ENUM_VAL2_VALUE = 11
    }
}  
""")
        expect:
        for (def field : element.fields) {
            if (field.name == 'ENUM_VAL1_VALUE') {
                field.constantValue == 10
            } else if (field.name == 'ENUM_VAL2_VALUE') {
                field.constantValue == 11
            }
        }
    }
}
