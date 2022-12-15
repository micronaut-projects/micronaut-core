package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ElementModifier

class ClassElementSpec extends AbstractKotlinCompilerSpec {

    void "test class element"() {
        given:
        def classElement = buildClassElement('ast.test.Test', '''
package ast.test

class Test(
    val publicConstructorReadOnly : String,
    private val privateConstructorReadOnly : String,
    protected val protectedConstructorReadOnly : Boolean
) : Parent() {

    val publicReadOnlyProp : Boolean = true
    protected val protectedReadOnlyProp : Boolean? = true
    private val privateReadOnlyProp : Boolean? = true
    var publicReadWriteProp : Boolean = true
    protected var protectedReadWriteProp : String? = "ok"
    private var privateReadWriteProp : String = "ok"

    private fun privateFunc(name : String) : String {
        return "ok"
    }

    protected fun protectedFunc(name : String) : String {
        return "ok"
    }

    fun publicFunc(name : String) : String {
        return "ok"
    }

    suspend fun suspendFunc(name : String) : String {
        return "ok"
    }
}

open class Parent {

}
''')
        expect:
        !classElement.isAbstract()
        classElement.name == 'ast.test.Test'
        !classElement.isPrivate()
        classElement.isPublic()
        classElement.modifiers == [ElementModifier.FINAL, ElementModifier.PUBLIC] as Set
    }
}
