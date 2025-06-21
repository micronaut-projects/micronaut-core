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

import com.google.devtools.ksp.symbol.KSPropertySetter
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getVisibility

internal class KotlinPropertySetterMethodElement(
    private val owningType: KotlinClassElement,
    private val propertyElement: KotlinPropertyElement,
    private val propertySetter: KSPropertySetter,
    private val presetParameters: List<ParameterElement>?,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
) : AbstractKotlinPropertyAccessorMethodElement<KotlinPropertySetterNativeElement>(
    KotlinPropertySetterNativeElement(propertySetter),
    propertySetter,
    propertySetter.getVisibility(),
    owningType,
    elementAnnotationMetadataFactory,
    visitorContext
), MethodElement {

    constructor(
        owningType: KotlinClassElement,
        propertyElement: KotlinPropertyElement,
        method: KSPropertySetter,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
    ) : this(
        owningType,
        propertyElement,
        method,
        null,
        elementAnnotationMetadataFactory,
        visitorContext
    )

    override val internalDeclaredTypeArguments: Map<String, ClassElement> = emptyMap()

    override val internalReturnType: ClassElement = PrimitiveElement.VOID

    override val internalGenericReturnType: ClassElement = PrimitiveElement.VOID

    override val resolvedParameters: List<ParameterElement> by lazy {
        presetParameters
            ?: listOf(
                KotlinParameterElement(
                    propertyElement.type,
                    this,
                    propertySetter.parameter,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
            )
    }

    override fun isSynthetic() = true

    override fun withNewOwningType(owningType: ClassElement): MethodElement {
        val newMethod = KotlinPropertySetterMethodElement(
            owningType as KotlinClassElement,
            propertyElement,
            propertySetter,
            presetParameters,
            elementAnnotationMetadataFactory,
            visitorContext,
        )
        copyValues(newMethod)
        return newMethod
    }

    override fun copyThis() = KotlinPropertySetterMethodElement(
        owningType,
        propertyElement,
        propertySetter,
        presetParameters,
        elementAnnotationMetadataFactory,
        visitorContext,
    )

    override fun withParameters(vararg newParameters: ParameterElement) =
        KotlinPropertySetterMethodElement(
            owningType,
            propertyElement,
            propertySetter,
            newParameters.toList(),
            elementAnnotationMetadataFactory,
            visitorContext,
        )

}
