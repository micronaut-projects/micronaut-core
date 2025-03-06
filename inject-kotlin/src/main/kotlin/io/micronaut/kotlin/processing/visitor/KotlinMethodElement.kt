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

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getBinaryName
import java.util.function.Function
import java.util.stream.Collectors

@OptIn(KspExperimental::class)
internal open class KotlinMethodElement(
    owningType: KotlinClassElement,
    override val declaration: KSFunctionDeclaration,
    private val presetParameters: List<ParameterElement>?,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinMethodElement<KotlinMethodNativeElement>(
    KotlinMethodNativeElement(declaration),
    declaration.getBinaryName(visitorContext.resolver),
    owningType,
    elementAnnotationMetadataFactory,
    visitorContext
), MethodElement {

    constructor(
        owningType: KotlinClassElement,
        declaration: KSFunctionDeclaration,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext
    ) : this(
        owningType,
        declaration,
        null,
        elementAnnotationMetadataFactory,
        visitorContext
    )

    override val internalDeclaringType: ClassElement by lazy {
        resolveDeclaringType(declaration, owningType)
    }

    override val internalDeclaredTypeArguments: Map<String, ClassElement> by lazy {
        resolveTypeArguments(nativeType, declaration, declaringType.typeArguments)
    }

    override val resolvedParameters: List<ParameterElement> by lazy {
        presetParameters
            ?: declaration.parameters.map {
                KotlinParameterElement(
                    null,
                    this,
                    it,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
            }
    }

    override val internalReturnType: ClassElement by lazy {
        newClassElement(nativeType, declaration.returnType!!.resolve(), emptyMap())
    }

    override val internalGenericReturnType: ClassElement by lazy {
        newClassElement(nativeType, declaration.returnType!!.resolve(), declaringType.typeArguments)
    }

    override fun isAbstract(): Boolean = declaration.isAbstract

    override fun isPublic(): Boolean = declaration.isPublic()

    override fun isProtected(): Boolean = declaration.isProtected()

    override fun isPrivate(): Boolean = declaration.isPrivate()

    override fun isSynthetic() =
        declaration.functionKind != FunctionKind.MEMBER && declaration.functionKind != FunctionKind.STATIC

    override fun isSuspend() = declaration.modifiers.contains(Modifier.SUSPEND)

    override fun getOverriddenMethods(): Collection<MethodElement> {
        return visitorContext.nativeElementsHelper
            .findOverriddenMethods(owningType.declaration, declaration)
            .stream()
            .map { KotlinMethodElement(
                    owningType,
                    it,
                    presetParameters,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
            }.collect(Collectors.toList())
    }

    override fun withNewOwningType(owningType: ClassElement): MethodElement {
        val newMethod = KotlinMethodElement(
            owningType as KotlinClassElement,
            declaration,
            presetParameters,
            elementAnnotationMetadataFactory,
            visitorContext,
        )
        copyValues(newMethod)
        return newMethod
    }

    override fun copyThis(): KotlinMethodElement {
        return KotlinMethodElement(
            owningType,
            declaration,
            presetParameters,
            elementAnnotationMetadataFactory,
            visitorContext,
        )
    }

    override fun withParameters(vararg newParameters: ParameterElement) =
        KotlinMethodElement(
            owningType,
            declaration,
            newParameters.toList(),
            elementAnnotationMetadataFactory,
            visitorContext,
        )
}
