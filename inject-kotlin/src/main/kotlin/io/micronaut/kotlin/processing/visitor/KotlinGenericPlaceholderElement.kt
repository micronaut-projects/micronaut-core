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

import com.google.devtools.ksp.symbol.KSTypeParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.GenericPlaceholderElement
import java.util.*

class KotlinGenericPlaceholderElement(
    classType: KSTypeParameter,
    annotationMetadata: AnnotationMetadata,
    visitorContext: KotlinVisitorContext,
    private val arrayDimensions: Int = 0
) : AbstractKotlinElement<KSTypeParameter>(classType, annotationMetadata, visitorContext), ArrayableClassElement, GenericPlaceholderElement {


    override fun getName(): String = "java.lang.Object"

    override fun isAssignable(type: String?): Boolean = false

    override fun isArray(): Boolean = arrayDimensions > 0

    override fun getArrayDimensions(): Int = arrayDimensions

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinGenericPlaceholderElement(declaration, annotationMetadata, visitorContext, arrayDimensions)
    }

    override fun getBounds(): MutableList<out ClassElement> {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val elementFactory = visitorContext.elementFactory
        return declaration.bounds.map {
            val argumentType = it.resolve()
            elementFactory.newClassElement(argumentType, annotationUtils.getAnnotationMetadata(argumentType.declaration))
        }.toMutableList()
    }

    override fun getVariableName(): String {
        return declaration.name.asString()
    }

    override fun getDeclaringElement(): Optional<Element> {
        return Optional.empty()
    }

    override fun withNewMetadata(annotationMetadata: AnnotationMetadata): ClassElement {
        return KotlinGenericPlaceholderElement(declaration, annotationMetadata, visitorContext, arrayDimensions)
    }
}
