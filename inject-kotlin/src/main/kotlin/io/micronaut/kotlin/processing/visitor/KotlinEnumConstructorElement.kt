package io.micronaut.kotlin.processing.visitor

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement

class KotlinEnumConstructorElement(private val classElement: ClassElement): MethodElement {

    override fun getName(): String = "valueOf"

    override fun isProtected() = false

    override fun isPublic() = true

    override fun getNativeType(): Any {
        throw UnsupportedOperationException("No native type backing a kotlin enum static constructor")
    }

    override fun isStatic(): Boolean = true

    override fun getDeclaringType(): ClassElement = classElement

    override fun getReturnType(): ClassElement = classElement

    override fun getParameters(): Array<ParameterElement> {
        return arrayOf(ParameterElement.of(String::class.java, "s"))
    }

    override fun withNewParameters(vararg newParameters: ParameterElement?): MethodElement {
        throw UnsupportedOperationException("Cannot replace parameters of a kotlin enum static constructor")
    }
}
