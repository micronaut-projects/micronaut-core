package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement

open class KotlinMethodElement(
    private val method: KSDeclaration,
    private val declaringType: ClassElement,
    annotationMetadata: AnnotationMetadata,
    private val visitorContext: KotlinVisitorContext,
    private val returnType: ClassElement,
    private val parameters: List<ParameterElement>
) : AbstractKotlinElement(method, annotationMetadata, visitorContext), MethodElement {

    override fun getName(): String {
        return method.simpleName.asString()
    }

    override fun getDeclaringType(): ClassElement {
        return declaringType
    }

    override fun getReturnType(): ClassElement {
        return returnType
    }

    override fun getParameters(): Array<ParameterElement> {
        return parameters.toTypedArray()
    }

    override fun withNewParameters(vararg newParameters: ParameterElement): MethodElement {
        return KotlinMethodElement(method, declaringType, annotationMetadata, visitorContext, returnType, newParameters.toList())
    }
}
