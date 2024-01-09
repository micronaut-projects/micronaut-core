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

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSPropertyGetter
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

internal class KotlinPropertyGetterMethodElement(
    private val owningType: KotlinClassElement,
    private val propertyElement: KotlinPropertyElement,
    private val propertyGetter: KSPropertyGetter,
    private val presetParameters: List<ParameterElement>,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
) : AbstractKotlinPropertyAccessorMethodElement<KotlinPropertyGetterNativeElement>(
    KotlinPropertyGetterNativeElement(propertyGetter),
    propertyGetter,
    propertyGetter.receiver.getVisibility(),
    owningType,
    elementAnnotationMetadataFactory,
    visitorContext
), MethodElement {

    constructor(
        owningType: ClassElement,
        method: KSPropertyGetter,
        propertyElement: KotlinPropertyElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext
    ) : this(
        owningType as KotlinClassElement,
        propertyElement,
        method,
        emptyList(),
        elementAnnotationMetadataFactory,
        visitorContext
    )

    override val internalReturnType: ClassElement = propertyElement.type

    override val internalGenericReturnType: ClassElement = propertyElement.genericType

    override val resolvedParameters: List<ParameterElement> = presetParameters

    override fun withNewOwningType(owningType: ClassElement): MethodElement {
        val newMethod = KotlinPropertyGetterMethodElement(
            owningType as KotlinClassElement,
            propertyElement,
            propertyGetter,
            presetParameters,
            elementAnnotationMetadataFactory,
            visitorContext,
        )
        copyValues(newMethod)
        return newMethod
    }

    override fun copyThis() =
        KotlinPropertyGetterMethodElement(
            owningType,
            propertyElement,
            propertyGetter,
            presetParameters,
            elementAnnotationMetadataFactory,
            visitorContext,
        )

    override fun withParameters(vararg newParameters: ParameterElement) =
        KotlinPropertyGetterMethodElement(
            owningType,
            propertyElement,
            propertyGetter,
            newParameters.toList(),
            elementAnnotationMetadataFactory,
            visitorContext,
        )

}
