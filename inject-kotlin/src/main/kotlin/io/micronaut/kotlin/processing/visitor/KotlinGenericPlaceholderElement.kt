package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSTypeParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.GenericPlaceholderElement
import java.util.*

class KotlinGenericPlaceholderElement(
    classType: KSTypeParameter,
    annotationMetadata: AnnotationMetadata,
    visitorContext: KotlinVisitorContext,
    private val arrayDimensions: Int = 0
) : AbstractKotlinElement<KSTypeParameter>(classType, annotationMetadata, visitorContext), ArrayableClassElement, GenericPlaceholderElement {


    override fun getName(): String = "java.lang.Object"

    override fun isAssignable(type: String?): Boolean = false

    override fun getArrayDimensions(): Int = arrayDimensions

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinGenericPlaceholderElement(declaration, annotationMetadata, visitorContext, arrayDimensions)
    }

    override fun getBounds(): MutableList<out ClassElement> {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val elementFactory = visitorContext.elementFactory
        return declaration.bounds.map {
            val argumentType = it.resolve()
            elementFactory.newClassElement(argumentType, annotationUtils.getAnnotationMetadata(argumentType.declaration))
        }.toMutableList()
    }

    override fun getVariableName(): String {
        return declaration.name.asString()
    }

    override fun getDeclaringElement(): Optional<Element> {
        return Optional.empty()
    }
}
