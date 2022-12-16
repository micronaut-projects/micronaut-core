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

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getBinaryName
import java.util.*

class KotlinGenericPlaceholderElement(
    private val parameter: KSTypeParameter,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
    private val arrayDimensions: Int = 0
) : KotlinClassElement(parameter, elementAnnotationMetadataFactory, visitorContext, arrayDimensions, true), ArrayableClassElement, GenericPlaceholderElement {
    override fun copyThis(): KotlinGenericPlaceholderElement {
        return KotlinGenericPlaceholderElement(
            parameter,
            annotationMetadataFactory,
            visitorContext,
            arrayDimensions
        )
    }

    override fun getName(): String {
        val bounds = parameter.bounds.firstOrNull()
        if (bounds != null) {
            return bounds.resolve().declaration.getBinaryName(visitorContext.resolver)
        }
        return "java.lang.Object"
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): ClassElement {
        return super<KotlinClassElement>.withAnnotationMetadata(annotationMetadata) as ClassElement
    }

    override fun isArray(): Boolean = arrayDimensions > 0

    override fun getArrayDimensions(): Int = arrayDimensions

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinGenericPlaceholderElement(parameter, annotationMetadataFactory, visitorContext, arrayDimensions)
    }

    override fun getBounds(): MutableList<out ClassElement> {
        val elementFactory = visitorContext.elementFactory
        val resolved = parameter.bounds.map {
            val argumentType = it.resolve()
            elementFactory.newClassElement(argumentType, annotationMetadataFactory)
        }.toMutableList()
        return if (resolved.isEmpty()) {
            mutableListOf(visitorContext.getClassElement(Object::class.java.name).get())
        } else {
            resolved
        }
    }

    override fun getVariableName(): String {
        return parameter.simpleName.asString()
    }

    override fun getDeclaringElement(): Optional<Element> {
        val classDeclaration = parameter.closestClassDeclaration()
        return Optional.ofNullable(classDeclaration).map {
            visitorContext.elementFactory.newClassElement(
                classDeclaration!!.asStarProjectedType(),
                visitorContext.elementAnnotationMetadataFactory)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as KotlinGenericPlaceholderElement

        if (parameter.simpleName.asString() != other.parameter.simpleName.asString()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + parameter.simpleName.asString().hashCode()
        return result
    }


}
