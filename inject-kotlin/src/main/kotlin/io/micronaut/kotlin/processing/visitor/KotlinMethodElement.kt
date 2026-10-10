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
import java.util.stream.Collectors

internal open class KotlinMethodElement(
    owningType: KotlinClassElement,
    override val declaration: KSFunctionDeclaration,
    private val presetParameters: List<ParameterElement>?,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinMethodElement<KotlinMethodNativeElement>(
    KotlinMethodNativeElement(declaration),
    visitorContext.getBinaryName(declaration),
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
            ?: buildList {
                declaration.extensionReceiver?.let { extensionReceiver ->
                    add(
                        ParameterElement.of(
                            newClassElement(nativeType, extensionReceiver.resolve(), emptyMap()),
                            "\$this"
                        )
                    )
                }
                addAll(declaration.parameters.map {
                    KotlinParameterElement(
                        null,
                        this@KotlinMethodElement,
                        it,
                        elementAnnotationMetadataFactory,
                        visitorContext
                    )
                })
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

    /**
     * The methods this method overrides as a member of its owning type, as javac reports them: the methods it
     * overrides in the hierarchy of its declaring type and, for an inherited method, the methods it implements of
     * the interfaces the owning type introduces.
     */
    override fun getOverriddenMethods(): Collection<MethodElement> {
        val overriddenMethods: MutableList<MethodElement> = visitorContext.nativeElementsHelper
            .findOverriddenMethods(owningType.declaration, declaration)
            .stream()
            .map { KotlinMethodElement(
                    owningType,
                    it,
                    presetParameters,
                    elementAnnotationMetadataFactory,
                    visitorContext
                ) as MethodElement
            }.collect(Collectors.toList())
        for (implementedMethod in findImplementedMethods()) {
            if (overriddenMethods.none { it.declaringType.name == implementedMethod.declaringType.name }) {
                overriddenMethods.add(implementedMethod)
            }
        }
        return overriddenMethods
    }

    /**
     * The methods an inherited method implements of the interfaces the owning type, or a class between it and the
     * declaring type, introduces. The interfaces the declaring type implements itself are covered by the methods it
     * overrides in the hierarchy of its declaring type.
     */
    private fun findImplementedMethods(): List<MethodElement> {
        val declaringType = getDeclaringType()
        if (isAbstract || isStatic || isPrivate || declaringType.name == owningType.name) {
            return emptyList()
        }
        val implementedMethods = mutableListOf<MethodElement>()
        var type: ClassElement? = owningType
        while (type != null && type.name != declaringType.name) {
            for (anInterface in type.interfaces) {
                if (declaringType.isAssignable(anInterface)) {
                    continue
                }
                for (candidate in anInterface.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().named(name))) {
                    if (!candidate.isPrivate && isSubSignature(candidate) && !implementedMethods.contains(candidate)) {
                        implementedMethods.add(candidate)
                    }
                }
            }
            type = type.superType.orElse(null)
        }
        return implementedMethods
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
