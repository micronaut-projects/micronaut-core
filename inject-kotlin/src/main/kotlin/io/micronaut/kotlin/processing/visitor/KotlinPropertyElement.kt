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
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.*

internal class KotlinPropertyElement(
    ownerType: KotlinClassElement,
    val property: KSPropertyDeclaration,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
) : AbstractKotlinPropertyElement<KotlinPropertyNativeElement>(
    KotlinPropertyNativeElement(property),
    ownerType,
    property.simpleName.asString(),
    false,
    elementAnnotationMetadataFactory, visitorContext
), PropertyElement {

    override val declaration = property

    override val abstract = property.isAbstract()

    override val resolvedType: ClassElement by lazy {
        newClassElement(nativeType, property.type.resolve(), emptyMap())
    }

    override val resolvedGenericType: ClassElement by lazy {
        newClassElement(nativeType, property.type.resolve(), declaringType.typeArguments)
    }

    override val setter: Optional<MethodElement> by lazy {
        Optional.ofNullable(property.setter)
            .map { method ->
                val modifiers = try {
                    method.modifiers
                } catch (e: IllegalStateException) {
                    // KSP bug: IllegalStateException: unhandled visibility: invisible_fake
                    setOf(Modifier.INTERNAL)
                }
                return@map if (modifiers.contains(Modifier.PRIVATE)) {
                    null
                } else {
                    KotlinPropertySetterMethodElement(
                        ownerType,
                        this,
                        method,
                        elementAnnotationMetadataFactory,
                        visitorContext
                    )
                }
            }
    }

    override val getter: Optional<MethodElement> by lazy {
        Optional.ofNullable(property.getter)
            .map { method ->
                KotlinPropertyGetterMethodElement(
                    owningType,
                    method,
                    this,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
            }
    }

    override val fieldElement: Optional<FieldElement> by lazy {
        if (property.hasBackingField) {
            val newFieldElement = visitorContext.elementFactory.newFieldElement(
                ownerType,
                property,
                elementAnnotationMetadataFactory
            )
            Optional.of(newFieldElement)
        } else {
            Optional.empty()
        }
    }

    override val constructorParameter: Optional<ParameterElement> by lazy {
        val kotlinDeclaringType = declaringType as KotlinClassElement
        val kotlinDeclaration = kotlinDeclaringType.declaration
        if (kotlinDeclaration.modifiers.contains(Modifier.DATA)) {
            val parameter =
                kotlinDeclaration.primaryConstructor?.parameters?.find { it.name?.asString() == name }
            if (parameter == null) {
                Optional.empty()
            } else {
                val constructor = KotlinMethodElement(
                    kotlinDeclaringType,
                    kotlinDeclaration.primaryConstructor as KSFunctionDeclaration,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
                Optional.of(
                    KotlinParameterElement(
                        null,
                        constructor,
                        parameter,
                        elementAnnotationMetadataFactory,
                        visitorContext
                    )
                )
            }
        } else {
            Optional.empty()
        }
    }

    override fun copyThis() = KotlinPropertyElement(
        owningType,
        property,
        elementAnnotationMetadataFactory,
        visitorContext
    )

}
