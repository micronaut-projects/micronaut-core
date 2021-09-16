package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSValueParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ParameterElement

class KotlinParameterElement(
    private val classElement: KotlinClassElement,
    private val parameter: KSValueParameter,
    annotationMetadata: AnnotationMetadata,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinElement(parameter, annotationMetadata, visitorContext), ParameterElement {

    override fun getName(): String {
        return parameter.name!!.asString()
    }

    override fun getType(): ClassElement = classElement
}
