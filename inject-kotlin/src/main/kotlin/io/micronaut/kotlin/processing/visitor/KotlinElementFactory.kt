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

import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.isTypeReference

class KotlinElementFactory(
    private val visitorContext: KotlinVisitorContext): ElementFactory<Any, KSType, KSFunctionDeclaration, KSPropertyDeclaration> {

    companion object {
        val primitives = mapOf(
            "kotlin.Boolean" to PrimitiveElement.BOOLEAN,
            "kotlin.Char" to PrimitiveElement.CHAR,
            "kotlin.Short" to PrimitiveElement.SHORT,
            "kotlin.Int" to PrimitiveElement.INT,
            "kotlin.Long" to PrimitiveElement.LONG,
            "kotlin.Float" to PrimitiveElement.FLOAT,
            "kotlin.Double" to PrimitiveElement.DOUBLE,
            "kotlin.Byte" to PrimitiveElement.BYTE,
            "kotlin.Unit" to PrimitiveElement.VOID
        )
        val primitiveArrays = mapOf(
            "kotlin.BooleanArray" to PrimitiveElement.BOOLEAN.toArray(),
            "kotlin.CharArray" to PrimitiveElement.CHAR.toArray(),
            "kotlin.ShortArray" to PrimitiveElement.SHORT.toArray(),
            "kotlin.IntArray" to PrimitiveElement.INT.toArray(),
            "kotlin.LongArray" to PrimitiveElement.LONG.toArray(),
            "kotlin.FloatArray" to PrimitiveElement.FLOAT.toArray(),
            "kotlin.DoubleArray" to PrimitiveElement.DOUBLE.toArray(),
            "kotlin.ByteArray" to PrimitiveElement.BYTE.toArray(),
        )
    }

    fun newClassElement(
        type: KSType
    ): ClassElement {
        return newClassElement(type, visitorContext.elementAnnotationMetadataFactory)
    }

    fun newClassElement(
        type: KSType,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        return newClassElement(type, visitorContext.elementAnnotationMetadataFactory, resolvedGenerics)
    }

    override fun newClassElement(
        type: KSType,
        annotationMetadataFactory: ElementAnnotationMetadataFactory
    ): ClassElement {
        return newClassElement(
            type,
            annotationMetadataFactory,
            emptyMap(),
            true
        )
    }

    override fun newClassElement(
        type: KSType,
        annotationMetadataFactory: ElementAnnotationMetadataFactory,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        return newClassElement(
            type,
            annotationMetadataFactory,
            resolvedGenerics,
            true
        )
    }

    fun newClassElement(type: KSType,
                        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                        resolvedGenerics: Map<String, ClassElement>,
                        allowPrimitive: Boolean): ClassElement {
        val declaration = type.declaration
        val qualifiedName = declaration.qualifiedName!!.asString()
        val hasNoAnnotations = !type.annotations.iterator().hasNext()
        var element = primitiveArrays[qualifiedName]
        if (hasNoAnnotations && element != null) {
            return element
        }
        if (qualifiedName == "kotlin.Array") {
            val component = type.arguments[0].type!!.resolve()
            val componentElement = newClassElement(component, elementAnnotationMetadataFactory, resolvedGenerics, false)
            return componentElement.toArray()
        } else if (declaration is KSTypeParameter) {
            val name = declaration.name.asString()
            return if (resolvedGenerics.containsKey(name)) {
                resolvedGenerics[name]!!
            } else {
                KotlinGenericPlaceholderElement(declaration, elementAnnotationMetadataFactory, visitorContext)
            }
        }
        if (allowPrimitive && !type.isMarkedNullable) {
            element = primitives[qualifiedName]
            if (hasNoAnnotations && element != null ) {
                return element
            }
        }
        return if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
            KotlinEnumElement(type, elementAnnotationMetadataFactory, visitorContext)
        } else {
            KotlinClassElement(type, elementAnnotationMetadataFactory, visitorContext, resolvedGenerics)
        }
    }

    override fun newSourceClassElement(
        type: KSType,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): ClassElement {
        return newClassElement(type, elementAnnotationMetadataFactory)
    }

    override fun newSourceMethodElement(
        owningClass: ClassElement,
        method: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): MethodElement {
        TODO("Not yet implemented")
        return newMethodElement(
            owningClass, method, elementAnnotationMetadataFactory
        )
    }

    override fun newMethodElement(
        owningClass: ClassElement,
        method: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): MethodElement {
        return newMethodElement(owningClass, method, elementAnnotationMetadataFactory, owningClass.typeArguments)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        typeArguments: Map<String, ClassElement>
    ): KotlinMethodElement {
        val returnType = method.returnType!!.resolve()

        val allTypeArguments = mutableMapOf<String, ClassElement>()
        allTypeArguments.putAll(typeArguments)
        method.typeParameters.forEach {
            allTypeArguments[it.name.asString()] = KotlinGenericPlaceholderElement(it, elementAnnotationMetadataFactory, visitorContext)
        }



        val returnTypeElement = newClassElement(returnType, elementAnnotationMetadataFactory)
        val genericReturnTypeElement = newClassElement(returnType, elementAnnotationMetadataFactory, allTypeArguments)

        val kotlinMethodElement = KotlinMethodElement(
            method,
            declaringClass,
            returnTypeElement,
            genericReturnTypeElement,
            elementAnnotationMetadataFactory,
            visitorContext,
            typeArguments
        )
        if (returnType.isMarkedNullable && !kotlinMethodElement.returnType.isPrimitive) {
            kotlinMethodElement.annotate(AnnotationUtil.NULLABLE)
        }
        return kotlinMethodElement
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertyGetter,
        type: ClassElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): MethodElement {
        return KotlinMethodElement(method, declaringClass, type, elementAnnotationMetadataFactory, visitorContext)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertySetter,
        type: ClassElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): MethodElement {
        return KotlinMethodElement(
            type,
            method,
            declaringClass,
            elementAnnotationMetadataFactory,
            visitorContext
        )
    }

    override fun newConstructorElement(
        owningClass: ClassElement,
        constructor: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): ConstructorElement {
        return KotlinConstructorElement(constructor, owningClass, elementAnnotationMetadataFactory, visitorContext, owningClass, emptyMap())
    }

    override fun newFieldElement(
        owningClass: ClassElement,
        field: KSPropertyDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory
    ): FieldElement {
        return KotlinFieldElement(field, owningClass, elementAnnotationMetadataFactory, visitorContext)
    }

    override fun newEnumConstantElement(
        owningClass: ClassElement?,
        enumConstant: KSPropertyDeclaration?,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory?
    ): EnumConstantElement {
        TODO("Not yet implemented")
    }
}
