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
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

class KotlinParameterElement(
    private val parameterType: ClassElement,
    private val methodElement: KotlinMethodElement,
    private val parameter: KSValueParameter,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KSValueParameter>(KSValueParameterReference(parameter), elementAnnotationMetadataFactory, visitorContext), ParameterElement {
    private val internalName : String by lazy {
        parameter.name!!.asString()
    }
    private val internalGenericType : ClassElement by lazy {
        resolveGeneric(
            methodElement.declaration.parent,
            parameterType,
            methodElement.owningType,
            visitorContext
        )
    }

    override fun isPrimitive(): Boolean {
        return parameterType.isPrimitive
    }

    override fun isArray(): Boolean {
        return parameterType.isArray
    }

    override fun copyThis(): AbstractKotlinElement<KSValueParameter> {
        return KotlinParameterElement(
            parameterType,
            methodElement,
            parameter,
            annotationMetadataFactory,
            visitorContext
        )
    }

    override fun getMethodElement(): MethodElement {
        return methodElement
    }

    override fun getName(): String {
        return internalName
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): ParameterElement {
        return super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as ParameterElement
    }

    override fun getType(): ClassElement = parameterType

    override fun getGenericType(): ClassElement {
        return internalGenericType
    }

    override fun getArrayDimensions(): Int = parameterType.arrayDimensions

}
