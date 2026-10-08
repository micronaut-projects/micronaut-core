package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery

class KotlinStaticModifierSpec extends AbstractKotlinCompilerSpec {

    void "test a @JvmStatic companion member is an instance member of the companion class"() {
        given:
        def result = buildClassElementMapped('test.Test$Companion', '''
package test

class Test {
    companion object {
        @JvmStatic
        fun staticFunc() = "a"

        fun plainFunc() = "b"
    }
}
''', { companion -> staticness(companion) })

        expect:
        result['staticFunc'] == [false, false]
        result['plainFunc'] == [false, false]
    }

    void "test a @JvmStatic object member is static"() {
        given:
        def result = buildClassElementMapped('test.Test', '''
package test

object Test {
    @JvmStatic
    fun staticFunc() = "a"

    fun plainFunc() = "b"
}
''', { obj -> staticness(obj) })

        expect:
        result['staticFunc'] == [true, true]
        result['plainFunc'] == [false, false]
    }

    void "test a companion object is a static nested class"() {
        given:
        def result = buildClassElementMapped('test.Test$Companion', '''
package test

class Test {
    companion object {
        fun plainFunc() = "b"
    }
}
''', { companion -> [companion.isStatic(), companion.modifiers.contains(ElementModifier.STATIC)] })

        expect:
        result == [true, true]
    }

    private static Map<String, List<Boolean>> staticness(io.micronaut.inject.ast.ClassElement classElement) {
        classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()).collectEntries {
            [it.name, [it.isStatic(), it.modifiers.contains(ElementModifier.STATIC)]]
        }
    }
}
