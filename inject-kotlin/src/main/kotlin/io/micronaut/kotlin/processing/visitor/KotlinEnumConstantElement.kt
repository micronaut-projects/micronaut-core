/*
 * Copyright 2017-2023 original authors
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

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

internal class KotlinEnumConstantElement(
    private val owningClass: ClassElement,
    val declaration: KSClassDeclaration,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext,
) : AbstractKotlinElement<KotlinEnumConstantNativeElement>(
    KotlinEnumConstantNativeElement(declaration),
    elementAnnotationMetadataFactory,
    visitorContext
), EnumConstantElement {

    override fun copyThis() =
        KotlinEnumConstantElement(
            owningClass,
            declaration,
            elementAnnotationMetadataFactory,
            visitorContext
        )

    override fun getDeclaringType(): ClassElement {
        return owningClass
    }

    override fun getType(): ClassElement {
        return owningClass
    }

    override fun getModifiers(): MutableSet<ElementModifier> = EnumConstantElement.ENUM_CONSTANT_MODIFIERS

    override fun getName(): String {
        return declaration.simpleName.asString()
    }

    override fun isPackagePrivate(): Boolean {
        return false
    }

    override fun isAbstract(): Boolean {
        return false
    }

    override fun isStatic(): Boolean {
        return true
    }

    override fun isPublic(): Boolean {
        return true
    }

    override fun isPrivate(): Boolean {
        return false
    }

    override fun isFinal(): Boolean {
        return true
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as FieldElement

    override fun isProtected(): Boolean {
        return false
    }

    override fun isPrimitive(): Boolean {
        return false
    }

    override fun isArray(): Boolean {
        return false
    }
}
