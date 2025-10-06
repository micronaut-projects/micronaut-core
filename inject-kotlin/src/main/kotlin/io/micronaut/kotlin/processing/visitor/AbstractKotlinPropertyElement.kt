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
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import io.micronaut.inject.ast.annotation.PropertyElementAnnotationMetadata
import java.util.*

internal abstract class AbstractKotlinPropertyElement<T : KotlinNativeElement>(
    nativeTypeDef: T,
    val ownerType: KotlinClassElement,
    private val name: String,
    private val excluded: Boolean,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
) : AbstractKotlinElement<T>(
    nativeTypeDef,
    elementAnnotationMetadataFactory,
    visitorContext
), PropertyElement {

    abstract val resolvedType: ClassElement
    abstract val resolvedGenericType: ClassElement
    abstract val setter: Optional<MethodElement>
    abstract val getter: Optional<MethodElement>
    abstract val fieldElement: Optional<FieldElement>
    open val constructorParameter: Optional<ParameterElement> = Optional.empty()
    abstract val abstract: Boolean
    abstract val declaration: KSDeclaration

    private val internalAnnotationMetadata: PropertyElementAnnotationMetadata by lazy {
        PropertyElementAnnotationMetadata(
            this,
            getter.orElse(null),
            setter.orElse(null),
            field.orElse(null),
            constructorParameter.orElse(null),
            true
        )
    }

    private val resolvedDeclaringType: ClassElement by lazy {
        resolveDeclaringType(declaration, owningType)
    }

    override fun overrides(overridden: PropertyElement?): Boolean {
        if (overridden == null || overridden !is AbstractKotlinPropertyElement<*>) {
            return false
        }
        val nativeType = nativeType.element
        val overriddenNativeType = overridden.nativeType.element
        if (nativeType == overriddenNativeType) {
            return false
        } else if (nativeType is KSPropertyDeclaration) {
            return overriddenNativeType == nativeType.findOverridee()
        }
        return false
    }

    override fun getDocumentation(parse: Boolean): Optional<String> {
        return Optional.ofNullable(declaration.docString)
    }

    override fun getWriteTypeAnnotationMetadata(): Optional<AnnotationMetadata> {
        return Optional.of(annotationMetadata.writeAnnotationMetadata)
    }

    override fun getReadTypeAnnotationMetadata(): Optional<AnnotationMetadata> {
        return Optional.of(annotationMetadata.readAnnotationMetadata)
    }

    override fun isExcluded() = excluded

    override fun getGenericType() = resolvedGenericType

    override fun getAnnotationMetadata() = internalAnnotationMetadata

    override fun getAnnotationMetadataToWrite() = internalAnnotationMetadata

    override fun getField() = fieldElement

    override fun getName() = name

    override fun getModifiers() = super<AbstractKotlinElement>.getModifiers()

    override fun getType() = resolvedType

    override fun getDeclaringType() = resolvedDeclaringType

    override fun getOwningType() = ownerType

    override fun getReadMethod() = getter

    override fun getWriteMethod() = setter

    override fun isAbstract() = abstract

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as MemberElement

    override fun isPrimitive() = type.isPrimitive

    override fun isArray() = type.isArray

    override fun getArrayDimensions() = type.arrayDimensions

    override fun isDeclaredNullable(): Boolean {
        val theType = resolvedType
        return theType is KotlinClassElement && theType.kotlinType.isMarkedNullable
    }

    override fun isNullable(): Boolean {
        val theType = resolvedType
        return theType is KotlinClassElement && theType.kotlinType.isMarkedNullable
    }

}
