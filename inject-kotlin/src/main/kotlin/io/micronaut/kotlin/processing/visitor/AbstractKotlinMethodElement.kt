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
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.util.ArrayUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate

internal abstract class AbstractKotlinMethodElement<T : KotlinNativeElement>(
    private val nativeType: T,
    private val name: String,
    private val owningType: KotlinClassElement,
    annotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<T>(nativeType, annotationMetadataFactory, visitorContext), MethodElement {

    abstract val declaration: KSDeclaration?
    abstract val internalDeclaringType: ClassElement
    abstract val internalDeclaredTypeArguments: Map<String, ClassElement>
    abstract val internalReturnType: ClassElement
    abstract val internalGenericReturnType: ClassElement
    abstract val resolvedParameters: List<ParameterElement>

    private val methodHelper by lazy {
        MethodElementAnnotationsHelper(this, annotationMetadataFactory)
    }

    override fun getMethodAnnotationMetadata(): ElementAnnotationMetadata =
        methodHelper.getMethodAnnotationMetadata(presetAnnotationMetadata)

    override fun getAnnotationMetadataToWrite(): MutableAnnotationMetadataDelegate<*> =
        methodHelper.getMethodAnnotationMetadata(presetAnnotationMetadata)

    override fun getAnnotationMetadata(): AnnotationMetadata =
        methodHelper.getAnnotationMetadata(presetAnnotationMetadata)

    override fun getModifiers() = super<AbstractKotlinElement>.getModifiers()

    override fun getDeclaredTypeArguments() = internalDeclaredTypeArguments

    override fun getDeclaredTypeVariables() =
        declaredTypeArguments.values.map { it as GenericPlaceholderElement }.toMutableList()

    override fun isDefault(): Boolean {
        return !isAbstract && declaringType.isAbstract
    }

    override fun isSuspend(): Boolean {
        val nativeType = nativeType
        return if (nativeType is KSModifierListOwner) {
            nativeType.modifiers.contains(Modifier.SUSPEND)
        } else {
            false
        }
    }

    override fun getSuspendParameters(): Array<ParameterElement> {
        return if (isSuspend) {
            val continuationParameter =
                visitorContext.getClassElement("kotlin.coroutines.Continuation")
                    .map {
                        var rt = internalGenericReturnType
                        if (rt.isPrimitive && rt.isVoid) {
                            rt = ClassElement.of(Unit::class.java)
                        }
                        val resolvedType = it.withTypeArguments(mapOf("T" to rt))
                        ParameterElement.of(
                            resolvedType,
                            "continuation"
                        )
                    }.orElse(null)
            if (continuationParameter != null) {
                ArrayUtils.concat(parameters, continuationParameter)
            } else {
                parameters
            }
        } else {
            parameters
        }
    }

    override fun overrides(overridden: MethodElement): Boolean {
        if (this == overridden || overridden !is AbstractKotlinMethodElement<*>) {
            return false
        }
        if (name != overridden.getName() || parameters.size != overridden.parameters.size) {
            return false // Fast escape
        }
        if (declaration != null && overridden.declaration != null) {
            return visitorContext.resolver.overrides(declaration!!, overridden.declaration!!)
        }
        return false
    }

    override fun hides(memberElement: MemberElement?) =
        // not sure how to implement this correctly for Kotlin
        false

    override fun hides(hiddenMethod: MethodElement?) =
        // not sure how to implement this correctly for Kotlin
        false

    override fun getName() = name

    override fun getOwningType() = owningType

    override fun getDeclaringType() = internalDeclaringType

    override fun getReturnType() = internalReturnType

    override fun getGenericReturnType() = internalGenericReturnType

    override fun getParameters() = resolvedParameters.toTypedArray()

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as MethodElement

    override fun toString(): String {
        return "$simpleName(" + parameters.joinToString(",") {
            if (it.type.isGenericPlaceholder) {
                (it.type as GenericPlaceholderElement).variableName
            } else {
                it.genericType.name
            }
        } + ")"
    }

    override fun getThrownTypes() = stringValues(Throws::class.java, "exceptionClasses")
        .flatMap {
            val ce = visitorContext.getClassElement(it).orElse(null)
            if (ce != null) {
                listOf(ce)
            } else {
                emptyList()
            }
        }.toTypedArray()

}
