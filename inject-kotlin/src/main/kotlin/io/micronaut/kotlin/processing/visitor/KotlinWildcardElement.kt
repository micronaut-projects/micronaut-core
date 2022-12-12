package io.micronaut.kotlin.processing.visitor

import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.function.Function

class KotlinWildcardElement(
    private val upperBounds: List<KotlinClassElement?>,
    private val lowerBounds: List<KotlinClassElement?>,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
    arrayDimensions: Int = 0
) : KotlinClassElement(
    upperBounds[0]!!.nativeType,
    elementAnnotationMetadataFactory,
    visitorContext,
    arrayDimensions,
    false
), WildcardElement {

    override fun foldBoundGenericTypes(@NonNull fold: Function<ClassElement?, ClassElement>): ClassElement? {
        val upperBounds: List<KotlinClassElement?> = this.upperBounds
            .map { ele ->
                toKotlinClassElement(
                    ele?.foldBoundGenericTypes(fold)
                )
            }.toList()
        val lowerBounds: List<KotlinClassElement?> = this.lowerBounds
            .map { ele ->
                toKotlinClassElement(
                    ele?.foldBoundGenericTypes(fold)
                )
            }.toList()
        return fold.apply(
            if (upperBounds.contains(null) || lowerBounds.contains(null)) null else KotlinWildcardElement(
                upperBounds, lowerBounds, elementAnnotationMetadataFactory, visitorContext, arrayDimensions
            )
        )
    }

    override fun getUpperBounds(): MutableList<out ClassElement?> {
        val list = mutableListOf<ClassElement?>()
        list.addAll(upperBounds)
        return list
    }

    override fun getLowerBounds(): MutableList<out ClassElement?> {
        val list = mutableListOf<ClassElement?>()
        list.addAll(lowerBounds)
        return list
    }

    private fun toKotlinClassElement(element: ClassElement?): KotlinClassElement {
        return if (element == null || element is KotlinClassElement) {
            element as KotlinClassElement
        } else {
            if (element.isWildcard || element.isGenericPlaceholder) {
                throw UnsupportedOperationException("Cannot convert wildcard / free type variable to JavaClassElement")
            } else {
                (visitorContext.getClassElement(element.name, elementAnnotationMetadataFactory)
                    .orElseThrow {
                        UnsupportedOperationException(
                            "Cannot convert ClassElement to JavaClassElement, class was not found on the visitor context"
                        )
                    } as ArrayableClassElement)
                    .withArrayDimensions(element.arrayDimensions)
                    .withBoundGenericTypes(element.boundGenericTypes) as KotlinClassElement
            }
        }
    }
}
