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

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

class KotlinFieldElement(declaration: KSPropertyDeclaration,
                         private val declaringType: ClassElement,
                         elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                         visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KSPropertyDeclaration>(declaration, elementAnnotationMetadataFactory, visitorContext), FieldElement {

    private val internalName = declaration.simpleName.asString()
    private val internalType : ClassElement by lazy {
        visitorContext.elementFactory.newClassElement(declaration.type.resolve())
    }

    private val internalGenericType : ClassElement by lazy {
        resolveGeneric(declaration.parent, type, declaringType, visitorContext)
    }

    override fun isPublic(): Boolean {
        return false // all Kotlin fields are private
    }

    override fun getType(): ClassElement {
        return internalType
    }

    override fun getName(): String {
        return internalName
    }

    override fun getGenericType(): ClassElement {
        return internalGenericType
    }

    override fun copyThis(): AbstractKotlinElement<KSPropertyDeclaration> {
        return KotlinFieldElement(declaration, declaringType, annotationMetadataFactory, visitorContext)
    }

    override fun isPrivate(): Boolean = true

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): FieldElement {
        return super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as FieldElement
    }

    override fun getDeclaringType() = declaringType

    override fun getModifiers(): MutableSet<ElementModifier> {
        return super<AbstractKotlinElement>.getModifiers()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KotlinFieldElement

        if (declaringType != other.declaringType) return false
        if (internalName != other.internalName) return false
        if (internalType != other.internalType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31 + declaringType.hashCode()
        result = 31 * result + internalName.hashCode()
        result = 31 * result + internalType.hashCode()
        return result
    }
}
