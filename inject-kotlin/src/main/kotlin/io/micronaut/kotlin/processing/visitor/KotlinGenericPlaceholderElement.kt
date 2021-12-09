package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.GenericPlaceholderElement
import java.util.*

class KotlinGenericPlaceholderElement(
    classType: KSType,
    annotationMetadata: AnnotationMetadata,
    visitorContext: KotlinVisitorContext,
    arrayDimensions: Int = 0
) : KotlinClassElement(classType, annotationMetadata, visitorContext, arrayDimensions), GenericPlaceholderElement {

    override fun getBounds(): MutableList<out ClassElement> {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val elementFactory = visitorContext.elementFactory
        return (classDeclaration as KSTypeParameter).bounds.map {
            val argumentType = it.resolve()
            elementFactory.newClassElement(argumentType, annotationUtils.getAnnotationMetadata(argumentType.declaration))
        }.toMutableList()
    }

    override fun getVariableName(): String {
        return (classDeclaration as KSTypeParameter).name.asString()
    }

    override fun getDeclaringElement(): Optional<Element> {
        return Optional.empty()
    }
}
