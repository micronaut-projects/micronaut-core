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

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementFactory
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

internal class KotlinElementFactory(
    private val visitorContext: KotlinVisitorContext
) : ElementFactory<Any, KSClassDeclaration, KSFunctionDeclaration, KSDeclaration> {

    fun newClassElement(
        type: KSClassDeclaration
    ): ClassElement {
        return newClassElement(type, visitorContext.elementAnnotationMetadataFactory)
    }

    override fun newClassElement(
        declaration: KSClassDeclaration,
        annotationMetadataFactory: ElementAnnotationMetadataFactory
    ): ClassElement {
        return if (declaration.classKind == ClassKind.ENUM_CLASS) {
            KotlinEnumElement(
                KotlinClassNativeElement(declaration),
                annotationMetadataFactory,
                visitorContext,
                null
            )
        } else {
            KotlinClassElement(
                KotlinClassNativeElement(declaration),
                annotationMetadataFactory,
                null,
                visitorContext,
                0,
                false
            )
        }
    }

    override fun newSourceClassElement(
        declaration: KSClassDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): ClassElement {
        return newClassElement(declaration, elementAnnotationMetadataFactory)
    }

    override fun newSourceMethodElement(
        owningClass: ClassElement,
        method: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): MethodElement {
        return newMethodElement(
            owningClass, method, elementAnnotationMetadataFactory
        )
    }

    override fun newMethodElement(
        owningClass: ClassElement,
        method: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): MethodElement {

        val kotlinMethodElement = KotlinMethodElement(
            owningClass,
            method,
            elementAnnotationMetadataFactory,
            visitorContext
        )
        if (method.returnType!!.resolve().isMarkedNullable && !kotlinMethodElement.returnType.isPrimitive) {
            kotlinMethodElement.annotate(AnnotationUtil.NULLABLE)
        }
        return kotlinMethodElement
    }

    override fun newConstructorElement(
        owningClass: ClassElement,
        constructor: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): ConstructorElement {
        return KotlinConstructorElement(
            owningClass,
            constructor,
            elementAnnotationMetadataFactory,
            visitorContext
        )
    }

    override fun newFieldElement(
        owningClass: ClassElement,
        field: KSDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): FieldElement {
        return KotlinFieldElement(
            owningClass,
            field as KSPropertyDeclaration,
            elementAnnotationMetadataFactory,
            visitorContext
        )
    }

    override fun newEnumConstantElement(
        owningClass: ClassElement,
        enumConstant: KSDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): EnumConstantElement {

        if (owningClass !is KotlinEnumElement) {
            throw IllegalArgumentException("Declaring class must be a KotlinEnumElement");
        }

        return KotlinEnumConstantElement(
            owningClass,
            enumConstant as KSClassDeclaration,
            elementAnnotationMetadataFactory,
            visitorContext
        )
    }
}
