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

import com.google.devtools.ksp.symbol.KSValueParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

internal class KotlinParameterElement(
    private val presetType: ClassElement?,
    private val methodElement: AbstractKotlinMethodElement<*>,
    private val parameter: KSValueParameter,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KotlinMethodParameterNativeElement>(
    KotlinMethodParameterNativeElement(parameter, methodElement.nativeType),
    elementAnnotationMetadataFactory,
    visitorContext
), io.micronaut.inject.ast.KotlinParameterElement {

    private val internalName: String by lazy {
        parameter.name!!.asString()
    }
    private val internalType: ClassElement by lazy {
        presetType ?: newClassElement(nativeType, parameter.type.resolve(), emptyMap())
    }
    private val internalGenericType: ClassElement by lazy {
        if (presetType != null) {
            if (presetType is KotlinClassElement) {
                val newCE = newClassElement(
                    nativeType,
                    presetType.kotlinType,
                    methodElement.typeArguments
                ) as ArrayableClassElement
                newCE.withArrayDimensions(presetType.arrayDimensions)
            } else {
                presetType
            }
        } else {
            newClassElement(nativeType, parameter.type.resolve(), methodElement.typeArguments)
        }
    }

    override fun isNonNull() = !hasDefault() && super<AbstractKotlinElement>.isNonNull()

    override fun isNullable() = hasDefault() || super<AbstractKotlinElement>.isNullable()

    override fun hasDefault() = parameter.hasDefault

    override fun isPrimitive() = internalType.isPrimitive

    override fun isArray() = internalType.isArray

    override fun copyThis() = KotlinParameterElement(
        presetType,
        methodElement,
        parameter,
        elementAnnotationMetadataFactory,
        visitorContext
    )

    override fun getMethodElement() = methodElement

    override fun getName() = internalName

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as ParameterElement

    override fun getType() = internalType

    override fun getGenericType() = internalGenericType

    override fun getArrayDimensions() = internalType.arrayDimensions

}
