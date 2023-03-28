/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.GenericPlaceholderElementAnnotationMetadata
import java.util.*
import java.util.function.Function

internal class KotlinGenericPlaceholderElement(
    private var internalGenericNativeType: KotlinTypeParameterNativeElement,
    private var upper: KotlinClassElement,
    private var resolved: KotlinClassElement?,
    private var bounds: List<KotlinClassElement>,
    private var declaringElement: Element?,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
    arrayDimensions: Int = resolved?.arrayDimensions ?: 0
) : KotlinClassElement(
    upper.nativeType,
    elementAnnotationMetadataFactory,
    upper.resolvedTypeArguments,
    visitorContext,
    arrayDimensions,
    true
), GenericPlaceholderElement {

    constructor(
        genericNativeType: KotlinTypeParameterNativeElement,
        resolved: KotlinClassElement?,
        bounds: List<KotlinClassElement>,
        declaringElement: Element?,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext
    ) : this(
        genericNativeType,
        selectClassElementRepresentingThisPlaceholder(resolved, bounds),
        resolved,
        bounds,
        declaringElement,
        elementAnnotationMetadataFactory,
        visitorContext
    )

    private val resolvedTypeAnnotationMetadata: ElementAnnotationMetadata by lazy {
        GenericPlaceholderElementAnnotationMetadata(this, upper)
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

    override fun copyThis() = KotlinGenericPlaceholderElement(
        genericNativeType,
        upper,
        resolved,
        bounds,
        declaringElement,
        elementAnnotationMetadataFactory,
        visitorContext,
        arrayDimensions
    )

    override fun isGenericPlaceholder() = true

    override fun getAnnotationMetadataToWrite() = resolvedGenericTypeAnnotationMetadata

    override fun getGenericTypeAnnotationMetadata() = resolvedGenericTypeAnnotationMetadata

    override fun getTypeAnnotationMetadata() = resolvedTypeAnnotationMetadata

    override fun getAnnotationMetadata() = resolvedAnnotationMetadata

    override fun getGenericNativeType() = internalGenericNativeType

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<KotlinClassElement>.withAnnotationMetadata(annotationMetadata)

    override fun withArrayDimensions(arrayDimensions: Int) = KotlinGenericPlaceholderElement(
        genericNativeType,
        upper,
        resolved,
        bounds,
        declaringElement,
        elementAnnotationMetadataFactory,
        visitorContext,
        arrayDimensions
    )

    override fun getBounds() = bounds

    override fun getVariableName() = genericNativeType.declaration.simpleName.asString()

    override fun getResolved(): Optional<ClassElement> = Optional.ofNullable(resolved)

    override fun getDeclaringElement(): Optional<Element> = Optional.ofNullable(declaringElement)

    override fun foldBoundGenericTypes(fold: Function<ClassElement?, ClassElement?>) =
        fold.apply(this)

    companion object {
        private fun selectClassElementRepresentingThisPlaceholder(
            resolved: KotlinClassElement?,
            bounds: List<KotlinClassElement>
        ) = resolved ?: WildcardElement.findUpperType(bounds, bounds)
    }
}
