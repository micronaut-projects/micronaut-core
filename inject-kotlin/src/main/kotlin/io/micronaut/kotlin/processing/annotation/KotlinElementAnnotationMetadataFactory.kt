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
package io.micronaut.kotlin.processing.annotation

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.GenericElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PackageElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.kotlin.processing.visitor.AbstractKotlinElement
import io.micronaut.kotlin.processing.visitor.AbstractKotlinMethodElement
import io.micronaut.kotlin.processing.visitor.KotlinClassNativeElement
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinEnumConstantElement
import io.micronaut.kotlin.processing.visitor.KotlinFieldElement
import io.micronaut.kotlin.processing.visitor.KotlinGenericPlaceholderElement
import io.micronaut.kotlin.processing.visitor.KotlinParameterElement
import io.micronaut.kotlin.processing.visitor.KotlinTypeArgumentElement
import io.micronaut.kotlin.processing.visitor.KotlinWildcardElement

internal class KotlinElementAnnotationMetadataFactory(
    isReadOnly: Boolean,
    metadataBuilder: KotlinAnnotationMetadataBuilder
) : AbstractElementAnnotationMetadataFactory<KSAnnotated, KSAnnotation>(
    isReadOnly,
    metadataBuilder
) {

    private val empty: KSAnnotated = KotlinAnnotations(emptySequence())

    override fun readOnly(): ElementAnnotationMetadataFactory {
        return KotlinElementAnnotationMetadataFactory(
            true,
            metadataBuilder as KotlinAnnotationMetadataBuilder
        )
    }

    override fun getNativeElement(element: Element?): KSAnnotated {
        return if (element is AbstractKotlinElement<*>) element.nativeType.element else empty
    }

    override fun buildGenericTypeAnnotations(element: GenericElement?): ElementAnnotationMetadata {
        if (element is KotlinTypeArgumentElement) {
            return buildTypeAnnotationsForTypeArgument(element)
        }
        return super.buildGenericTypeAnnotations(element)
    }

    private fun buildTypeAnnotationsForTypeArgument(
        element: KotlinTypeArgumentElement
    ): AbstractElementAnnotationMetadata {
        return object : AbstractElementAnnotationMetadata() {

            override fun lookup(): CachedAnnotationMetadata {
                if (element.genericNativeType.owner == null) {
                    throw ProcessingException(element, "Type annotations require the owner element to be specified!")
                }
                return metadataBuilder.lookupOrBuild(
                    element.genericNativeType,
                    element.genericNativeType.declaration
                )
            }

            override fun toString(): String {
                return element.toString()
            }
        }
    }

    override fun lookupForClass(classElement: ClassElement?): CachedAnnotationMetadata {
        val kotlinClassElement = classElement as KotlinClassElement
        return metadataBuilder.lookupOrBuild(
            getClassDefinitionCacheKey(kotlinClassElement),
            classElement.declaration
        )
    }

    override fun lookupTypeAnnotationsForClass(classElement: ClassElement): CachedAnnotationMetadata? {
        val kotlinClassElement = classElement as KotlinClassElement
        if (kotlinClassElement.nativeType.type == null) {
            throw ProcessingException(classElement, "Type annotations aren't supported!")
        }
        if (kotlinClassElement.nativeType.owner == null) {
            throw ProcessingException(classElement, "Type annotations require the owner element to be specified!")
        }
        return metadataBuilder.lookupOrBuild(
            kotlinClassElement.nativeType,
            KotlinAnnotations(kotlinClassElement.kotlinType.annotations)
        )
    }

    override fun lookupTypeAnnotationsForGenericPlaceholder(placeholderElement: GenericPlaceholderElement): CachedAnnotationMetadata? {
        val kotlinPlaceholderElement = placeholderElement as KotlinGenericPlaceholderElement
        if (kotlinPlaceholderElement.genericNativeType.owner == null) {
            throw ProcessingException(placeholderElement, "Type annotations an a generic placeholder require the owner element to be specified!")
        }
        return metadataBuilder.lookupOrBuild(
            kotlinPlaceholderElement.genericNativeType,
            kotlinPlaceholderElement.genericNativeType.declaration
        )
    }

    override fun lookupTypeAnnotationsForWildcard(wildcardElement: WildcardElement): CachedAnnotationMetadata? {
        val kotlinWildcardElement = wildcardElement as KotlinWildcardElement
        if (kotlinWildcardElement.genericNativeType.owner == null) {
            throw ProcessingException(wildcardElement, "Type annotations on a wildcard require the owner element to be specified!")
        }
        return metadataBuilder.lookupOrBuild(
            kotlinWildcardElement.genericNativeType,
            kotlinWildcardElement.genericNativeType.declaration
        )
    }

    override fun lookupForPackage(packageElement: PackageElement?): CachedAnnotationMetadata? {
        return metadataBuilder.lookupOrBuildForType(getNativeElement(packageElement))
    }

    override fun lookupForParameter(parameterElement: ParameterElement): CachedAnnotationMetadata? {
        val kotlinParameterElement = parameterElement as KotlinParameterElement
        val owner = kotlinParameterElement.methodElement.owningType as KotlinClassElement
        return metadataBuilder.lookupOrBuild(
            Key3(
                getClassDefinitionCacheKey(owner),
                kotlinParameterElement.methodElement.nativeType,
                kotlinParameterElement.nativeType
            ),
            kotlinParameterElement.nativeType.element
        )
    }

    override fun lookupForField(fieldElement: FieldElement): CachedAnnotationMetadata? {
        val kotlinFieldElement = fieldElement as AbstractKotlinElement<*>
        val owner: KotlinClassElement
        if (kotlinFieldElement is KotlinFieldElement) {
            owner = kotlinFieldElement.owningType as KotlinClassElement
        } else if (kotlinFieldElement is KotlinEnumConstantElement) {
            owner = kotlinFieldElement.owningType as KotlinClassElement
        } else {
            throw RuntimeException("Unknown field element type ${fieldElement.javaClass}")
        }
        return metadataBuilder.lookupOrBuild(
            Key2(
                getClassDefinitionCacheKey(owner),
                kotlinFieldElement.nativeType,
            ),
            kotlinFieldElement.nativeType.element
        )
    }

    override fun lookupForMethod(methodElement: MethodElement): CachedAnnotationMetadata? {
        val kotlinMethodElement = methodElement as AbstractKotlinMethodElement<*>
        val owner = kotlinMethodElement.owningType as KotlinClassElement
        return metadataBuilder.lookupOrBuild(
            Key2(
                getClassDefinitionCacheKey(owner),
                kotlinMethodElement.nativeType,
            ),
            kotlinMethodElement.nativeType.element
        )
    }

    private fun getClassDefinitionCacheKey(kotlinClassElement: KotlinClassElement) =
        KotlinClassNativeElement(
            kotlinClassElement.nativeType.declaration,
            null, // Strip the type for the cache key
        )

    data class Key2(private val v1: Any, private val v2: Any)

    data class Key3(private val v1: Any, private val v2: Any, private val v3: Any)

}
