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

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import java.lang.RuntimeException
import java.util.*

class KotlinPropertyElement: AbstractKotlinElement<KSNode>, PropertyElement {

    private val name: String
    private val classElement: ClassElement
    private val type: ClassElement
    private val setter: Optional<MethodElement>
    private val getter: Optional<MethodElement>
    private val abstract: Boolean

    constructor(classElement: ClassElement,
                type: ClassElement,
                property: KSPropertyDeclaration,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext) : super(property, annotationMetadata, visitorContext) {
        this.name = property.simpleName.asString()
        this.type = type
        this.classElement = classElement
        this.setter = Optional.ofNullable(property.setter)
            .map { method ->
                return@map if (method.modifiers.contains(Modifier.PRIVATE)) {
                    null
                } else {
                    visitorContext.elementFactory.newMethodElement(classElement, method, type, annotationMetadata)
                }
            }
        this.getter = Optional.ofNullable(property.getter)
            .map { method ->
                return@map visitorContext.elementFactory.newMethodElement(classElement, method, type, annotationMetadata)
            }
        this.abstract = property.isAbstract()
    }
    constructor(classElement: ClassElement,
                type: ClassElement,
                name: String,
                getter: KSFunctionDeclaration,
                setter: KSFunctionDeclaration?,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext) : super(getter, annotationMetadata, visitorContext) {
        this.name = name
        this.type = type
        this.classElement = classElement
        this.setter = Optional.ofNullable(setter)
            .map { method ->
                return@map visitorContext.elementFactory.newMethodElement(classElement, method, annotationMetadata)
            }
        this.getter = Optional.of(visitorContext.elementFactory.newMethodElement(classElement, getter, annotationMetadata))
        this.abstract = getter.isAbstract || setter?.isAbstract == true
    }

    override fun getName(): String = name

    override fun getType(): ClassElement = type

    override fun getDeclaringType(): ClassElement = classElement

    override fun getReadMethod(): Optional<MethodElement> = getter

    override fun getWriteMethod(): Optional<MethodElement> = setter

    override fun isReadOnly(): Boolean {
        return !setter.isPresent || setter.get().isPrivate
    }

    override fun isAbstract() = abstract
}
