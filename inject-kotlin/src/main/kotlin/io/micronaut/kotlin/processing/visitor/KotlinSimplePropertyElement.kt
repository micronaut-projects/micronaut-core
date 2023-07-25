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

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.*

internal class KotlinSimplePropertyElement(
    ownerType: ClassElement,
    private val type: ClassElement,
    name: String,
    private val internalFieldElement: FieldElement?,
    private val getterMethod: MethodElement?,
    private val setterMethod: MethodElement?,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
    excluded: Boolean = false
) : AbstractKotlinPropertyElement<KotlinSimplePropertyNativeElement>(
    KotlinSimplePropertyNativeElement(type, internalFieldElement, getterMethod, setterMethod),
    ownerType,
    name,
    excluded,
    elementAnnotationMetadataFactory,
    visitorContext
), PropertyElement {

    override val declaration: KSDeclaration by lazy {
        val ksAnnotated = nativeType.element
        when (ksAnnotated) {
            is KSDeclaration -> {
                ksAnnotated
            }

            is KSPropertyAccessor -> {
                ksAnnotated.receiver
            }

            else -> {
                throw IllegalStateException("Expected declaration got: $ksAnnotated")
            }
        }
    }

    override val resolvedType = type

    override val resolvedGenericType: ClassElement by lazy {
        if (type is KotlinClassElement) {
            val newCE = newClassElement(
                nativeType,
                type.kotlinType,
                declaringType.typeArguments
            ) as ArrayableClassElement
            newCE.withArrayDimensions(type.arrayDimensions)
        } else {
            type
        }
    }
    override val setter = Optional.ofNullable(setterMethod)

    override val getter = Optional.ofNullable(getterMethod)

    override val fieldElement: Optional<FieldElement> = Optional.ofNullable(internalFieldElement)

    override val abstract: Boolean by lazy {
        getterMethod?.isAbstract == true || setterMethod?.isAbstract == true
    }

    override fun copyThis() = KotlinSimplePropertyElement(
        ownerType,
        type,
        name,
        internalFieldElement,
        getterMethod,
        setterMethod,
        elementAnnotationMetadataFactory,
        visitorContext,
        isExcluded
    )

}
