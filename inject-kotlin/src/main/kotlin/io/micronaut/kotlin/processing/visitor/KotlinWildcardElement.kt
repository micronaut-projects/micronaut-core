/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.visitor

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.WildcardElementAnnotationMetadata
import java.util.function.Function

internal class KotlinWildcardElement(
    private val internalGenericNativeType: KotlinTypeArgumentNativeElement,
    private var upper: KotlinClassElement,
    private val upperBounds: List<KotlinClassElement?>,
    private val lowerBounds: List<KotlinClassElement?>,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
    private val isRawType: Boolean,
    arrayDimensions: Int = 0
) : KotlinClassElement(
    upper.nativeType,
    elementAnnotationMetadataFactory,
    upper.resolvedTypeArguments,
    visitorContext,
    arrayDimensions,
    true
), WildcardElement {

    private val resolvedTypeAnnotationMetadata: ElementAnnotationMetadata by lazy {
        WildcardElementAnnotationMetadata(this, upper)
    }
    private val resolvedAnnotationMetadata: AnnotationMetadata by lazy {
        AnnotationMetadataHierarchy(
            true,
            upper.annotationMetadata,
            resolvedGenericTypeAnnotationMetadata
        )
    }
    private val resolvedGenericTypeAnnotationMetadata: ElementAnnotationMetadata by lazy {
        elementAnnotationMetadataFactory.buildGenericTypeAnnotations(this)
    }

    override fun getAnnotationMetadataToWrite() = resolvedGenericTypeAnnotationMetadata

    override fun getGenericTypeAnnotationMetadata() = resolvedGenericTypeAnnotationMetadata

    override fun getTypeAnnotationMetadata() = resolvedTypeAnnotationMetadata

    override fun getAnnotationMetadata() = resolvedAnnotationMetadata

    override fun isRawType() = isRawType

    override fun getGenericNativeType() = internalGenericNativeType

    override fun foldBoundGenericTypes(@NonNull fold: Function<ClassElement?, ClassElement?>): ClassElement? {
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
                genericNativeType,
                upper,
                upperBounds,
                lowerBounds,
                elementAnnotationMetadataFactory,
                visitorContext,
                isRawType,
                arrayDimensions
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

    private fun toKotlinClassElement(element: ClassElement?): KotlinClassElement? {
        return when {
            element == null -> {
                null
            }

            element is KotlinClassElement -> {
                element
            }

            element.isWildcard || element.isGenericPlaceholder -> {
                throw UnsupportedOperationException("Cannot convert wildcard / free type variable to JavaClassElement")
            }

            else -> {
                (visitorContext.getClassElement(element.name, elementAnnotationMetadataFactory)
                    .orElseThrow {
                        UnsupportedOperationException(
                            "Cannot convert ClassElement to JavaClassElement, class was not found on the visitor context"
                        )
                    } as ArrayableClassElement)
                    .withArrayDimensions(element.arrayDimensions)
                    .withTypeArguments(element.typeArguments) as KotlinClassElement
            }
        }
    }
}
