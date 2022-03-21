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
import io.micronaut.inject.ast.ParameterElement

class KotlinParameterElement(
    private val genericClassElement: ClassElement,
    private val classElement: ClassElement,
    private val parameter: KSValueParameter,
    annotationMetadata: AnnotationMetadata,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KSValueParameter>(parameter, annotationMetadata, visitorContext), ParameterElement {

    override fun getName(): String {
        return parameter.name!!.asString()
    }

    override fun getType(): ClassElement = classElement

    override fun getGenericType(): ClassElement = genericClassElement

    override fun getArrayDimensions(): Int = classElement.arrayDimensions
}
