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
import io.micronaut.inject.ast.GenericElement
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import java.util.*
import kotlin.collections.ArrayList

internal class KotlinTypeArgumentElement(
    private var internalGenericNativeType: KotlinTypeArgumentNativeElement,
    private var resolved: KotlinClassElement,
    visitorContext: KotlinVisitorContext,
    internalArrayDimensions: Int = resolved.arrayDimensions
) : KotlinClassElement(
    resolved.nativeType,
    resolved.elementAnnotationMetadataFactory,
    resolved.resolvedTypeArguments,
    visitorContext,
    internalArrayDimensions,
    true
), GenericElement {

    private val resolvedTypeAnnotationMetadata: ElementAnnotationMetadata by lazy {
        class KotlinTypeArgumentElementAnnotationMetadata(
            private val typeArgumentElement: KotlinTypeArgumentElement,
            private val representingClassElement: ClassElement
        ) : AbstractElementAnnotationMetadata() {
            private var annotationMetadata: AnnotationMetadata? = null
            public override fun getReturnInstance(): AnnotationMetadata {
                return getAnnotationMetadata()
            }

            override fun getAnnotationMetadataToWrite(): MutableAnnotationMetadataDelegate<*> {
                return typeArgumentElement.genericTypeAnnotationMetadata
            }

            override fun getAnnotationMetadata(): AnnotationMetadata {
                if (annotationMetadata == null) {
                    val allAnnotationMetadata: MutableList<AnnotationMetadata> = ArrayList()
                    allAnnotationMetadata.add(representingClassElement.typeAnnotationMetadata)
                    allAnnotationMetadata.add(typeArgumentElement.genericTypeAnnotationMetadata)
                    annotationMetadata =
                        AnnotationMetadataHierarchy(true, *allAnnotationMetadata.toTypedArray())
                }
                return annotationMetadata!!
            }
        }
        KotlinTypeArgumentElementAnnotationMetadata(this, resolved)
    }

    private val resolvedAnnotationMetadata: AnnotationMetadata by lazy {
        AnnotationMetadataHierarchy(
            true,
            elementAnnotationMetadata,
            resolvedGenericTypeAnnotationMetadata
        )
    }

    private val resolvedGenericTypeAnnotationMetadata: ElementAnnotationMetadata by lazy {
        elementAnnotationMetadataFactory.buildGenericTypeAnnotations(this)
    }

    override fun getResolved(): Optional<ClassElement> = Optional.of(resolved)

    override fun getGenericNativeType() = internalGenericNativeType

    override fun getAnnotationMetadataToWrite() = resolvedGenericTypeAnnotationMetadata

    override fun getGenericTypeAnnotationMetadata() = resolvedGenericTypeAnnotationMetadata

    override fun getTypeAnnotationMetadata() = resolvedTypeAnnotationMetadata

    override fun getAnnotationMetadata() = resolvedAnnotationMetadata

    override fun withArrayDimensions(arrayDimensions: Int) = KotlinTypeArgumentElement(
        genericNativeType,
        resolved,
        visitorContext,
        arrayDimensions
    )

}
