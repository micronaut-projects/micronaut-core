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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isJavaPackagePrivate
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.Visibility
import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorBindingDefinitions
import io.micronaut.aop.Introduction
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.inject.ast.annotation.AbstractAnnotationElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.getClassDeclaration
import java.util.*
import kotlin.collections.HashSet

internal abstract class AbstractKotlinElement<T : KotlinNativeElement>(
    private val nativeType: T,
    annotationMetadataFactory: ElementAnnotationMetadataFactory,
    protected val visitorContext: KotlinVisitorContext
) : AbstractAnnotationElement(annotationMetadataFactory) {

    private val annotatedInfo = nativeType.element

    override fun getNativeType(): T = nativeType

    override fun isProtected() = if (annotatedInfo is KSDeclaration) {
        annotatedInfo.getVisibility() == Visibility.PROTECTED
    } else {
        false
    }

    override fun isStatic() = if (annotatedInfo is KSDeclaration) {
        annotatedInfo.modifiers.contains(Modifier.JAVA_STATIC)
    } else {
        false
    }

    private fun makeCopy(): AbstractKotlinElement<T> {
        val element: AbstractKotlinElement<T> = copyThis()
        copyValues(element)
        return element
    }

    /**
     * @return copy of this element
     */
    protected abstract fun copyThis(): AbstractKotlinElement<T>

    /**
     * @param element the values to be copied to
     */
    protected open fun copyValues(element: AbstractKotlinElement<T>) {
        element.presetAnnotationMetadata = presetAnnotationMetadata
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): Element? {
        val kotlinElement: AbstractKotlinElement<T> = makeCopy()
        kotlinElement.presetAnnotationMetadata = annotationMetadata
        return kotlinElement
    }

    override fun isPublic() = if (annotatedInfo is KSDeclaration) {
        annotatedInfo.getVisibility() == Visibility.PUBLIC
    } else {
        false
    }

    override fun isPrivate() = if (annotatedInfo is KSDeclaration) {
        annotatedInfo.getVisibility() == Visibility.PRIVATE
    } else {
        false
    }

    override fun isPackagePrivate() = if (annotatedInfo is KSDeclaration) {
        annotatedInfo.isJavaPackagePrivate()
    } else {
        false
    }

    override fun isFinal() = if (annotatedInfo is KSDeclaration) {
        if (annotatedInfo.modifiers.contains(Modifier.FINAL)) {
            true
        } else if (annotatedInfo.isOpen()) {
            false
        } else {
            // ksp does not see when all-open opens up these classes
            // https://github.com/micronaut-projects/micronaut-core/issues/9426
            // this logic is similar to what all-opens does.
            if (this is MemberElement) {
                !shouldBeOpen(owningType)
            } else {
                !shouldBeOpen(this)
            }
        }
    } else {
        false
    }

    private fun shouldBeOpen(annotationMetadata: AnnotationMetadata): Boolean {
        val extraOpenAnnotations = visitorContext.extraOpenAnnotations
        if (extraOpenAnnotations.isNotEmpty() && annotationMetadata.declaredMetadata.hasDeclaredStereotype(*extraOpenAnnotations)) {
            return true
        }
        return annotationMetadata.declaredMetadata.hasDeclaredStereotype(
            Around::class.java,
            Introduction::class.java,
            InterceptorBinding::class.java,
            InterceptorBindingDefinitions::class.java
        )
    }

    override fun isAbstract(): Boolean {
        return if (annotatedInfo is KSModifierListOwner) {
            annotatedInfo.modifiers.contains(Modifier.ABSTRACT)
        } else {
            false
        }
    }

    @OptIn(KspExperimental::class)
    override fun getModifiers(): MutableSet<ElementModifier> {
        if (annotatedInfo is KSDeclaration) {
            val javaModifiers = visitorContext.resolver.effectiveJavaModifiers(annotatedInfo)
            return javaModifiers.mapNotNull {
                when (it) {
                    Modifier.ABSTRACT -> ElementModifier.ABSTRACT
                    Modifier.FINAL -> ElementModifier.FINAL
                    Modifier.PRIVATE -> ElementModifier.PRIVATE
                    Modifier.PROTECTED -> ElementModifier.PROTECTED
                    Modifier.PUBLIC, Modifier.INTERNAL -> ElementModifier.PUBLIC
                    Modifier.JAVA_STATIC -> ElementModifier.STATIC
                    Modifier.JAVA_TRANSIENT -> ElementModifier.TRANSIENT
                    Modifier.JAVA_DEFAULT -> ElementModifier.DEFAULT
                    Modifier.JAVA_SYNCHRONIZED -> ElementModifier.SYNCHRONIZED
                    Modifier.JAVA_VOLATILE -> ElementModifier.VOLATILE
                    Modifier.JAVA_NATIVE -> ElementModifier.NATIVE
                    Modifier.JAVA_STRICT -> ElementModifier.STRICTFP
                    else -> null
                }
            }.toMutableSet()
        }
        return super.getModifiers()
    }

    override fun getDocumentation(): Optional<String> {
        return if (annotatedInfo is KSDeclaration) {
            Optional.ofNullable(annotatedInfo.docString)
        } else {
            Optional.empty()
        }
    }

    protected fun resolveDeclaringType(
        declaration: KSDeclaration,
        owningType: ClassElement
    ): ClassElement {
        var parent = declaration.parent
        if (parent is KSPropertyDeclaration) {
            parent = parent.parent
        }
        if (parent is KSFunctionDeclaration) {
            parent = parent.parent
        }
        return if (parent is KSClassDeclaration) {
            val className = parent.getBinaryName(visitorContext.resolver, visitorContext)
            if (owningType.name.equals(className)) {
                owningType
            } else {
                val parentTypeArguments = owningType.getTypeArguments(className)
                newKotlinClassElement(parent, parentTypeArguments)
            }
        } else {
            owningType
        }
    }

    protected fun resolveTypeArguments(
        owner: KotlinNativeElement,
        type: KSDeclaration,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any> = HashSet()
    ): Map<String, ClassElement> {
        val typeArguments = mutableMapOf<String, ClassElement>()
        val typeParameters = type.typeParameters
        typeParameters.forEachIndexed { i, typeParameter ->
            typeArguments[typeParameters[i].name.asString()] =
                resolveTypeParameter(owner, typeParameter, parentTypeArguments, visitedTypes)
        }
        return typeArguments
    }

    protected fun resolveTypeParameter(
        owner: KotlinNativeElement,
        typeParameter: KSTypeParameter,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any> = HashSet()
    ): ClassElement {
        val variableName = typeParameter.name.asString()
        val found = parentTypeArguments[variableName]
        if (found is PrimitiveElement) {
            return found
        }
        var bound = found as KotlinClassElement?
        if (bound is WildcardElement && !bound.isBounded) {
            bound = null
        }
        val parent = typeParameter.parent
        val thisNode = annotatedInfo
        val declaringElement = if (thisNode == parent) {
            this
        } else if (parent is KSClassDeclaration) {
            newKotlinClassElement(parent, emptyMap(), visitedTypes, true)
        } else {
            null
        }
        val stripTypeArguments = !visitedTypes.add(typeParameter)
        val bounds = typeParameter.bounds.map {
            val argumentType = it.resolve()
            newKotlinClassElement(
                owner,
                argumentType,
                parentTypeArguments,
                visitedTypes,
                stripTypeArguments
            )
        }.ifEmpty {
            mutableListOf(getJavaObjectClassElement()).asSequence()
        }.toList()

        return KotlinGenericPlaceholderElement(
            KotlinTypeParameterNativeElement(typeParameter, owner),
            bound,
            bounds,
            declaringElement,
            elementAnnotationMetadataFactory,
            visitorContext
        )
    }

    private fun getJavaObjectClassElement() =
        visitorContext.getClassElement(Object::class.java.name).get() as KotlinClassElement

    protected fun resolveTypeArguments(
        owner: KotlinNativeElement,
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any> = HashSet()
    ): Map<String, ClassElement> {
        val typeArguments = mutableMapOf<String, ClassElement>()
        val typeParameters = type.declaration.typeParameters
        if (type.arguments.isEmpty() || type.arguments.size != typeParameters.size) {
            typeParameters.forEach {
                typeArguments[it.name.asString()] =
                    resolveTypeParameter(owner, it, parentTypeArguments, visitedTypes)
            }
        } else {
            type.arguments.forEachIndexed { i, typeArgument ->
                val variableName = typeParameters[i].name.asString()
                typeArguments[variableName] =
                    resolveTypeArgument(owner, typeArgument, parentTypeArguments, visitedTypes)
            }
        }
        return typeArguments
    }

    private fun resolveEmptyTypeArguments(declaration: KSClassDeclaration): Map<String, ClassElement> {
        val objectElement = getJavaObjectClassElement()
        val typeArguments = mutableMapOf<String, ClassElement>()
        val typeParameters = declaration.typeParameters
        typeParameters.forEach {
            typeArguments[it.name.asString()] = objectElement
        }
        return typeArguments
    }

    private fun resolveTypeArgument(
        owner: KotlinNativeElement,
        typeArgument: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): ClassElement {

        return when (typeArgument.variance) {
            Variance.STAR, Variance.COVARIANT, Variance.CONTRAVARIANT -> {
                // example List<*>, IN, OUT
                val stripTypeArguments = when (typeArgument.type) {
                    null -> false
                    else -> !visitedTypes.add(typeArgument.type!!)
                }
                val upperBound =
                    resolveUpperBound(
                        owner,
                        typeArgument,
                        parentTypeArguments,
                        visitedTypes,
                        stripTypeArguments
                    )
                if (upperBound is PrimitiveElement) {
                    return upperBound
                }
                val lowerBound = resolveLowerBound(
                    owner,
                    typeArgument,
                    parentTypeArguments,
                    visitedTypes,
                    stripTypeArguments
                )
                if (lowerBound is PrimitiveElement) {
                    return upperBound
                }
                val upperBounds = listOf(upperBound as KotlinClassElement)
                val lowerBounds = if (lowerBound == null) listOf() else  listOf(lowerBound as KotlinClassElement)
                val upper = WildcardElement.findUpperType(upperBounds, lowerBounds)!!
                KotlinWildcardElement(
                    KotlinTypeArgumentNativeElement(typeArgument, owner),
                    upper,
                    upperBounds,
                    lowerBounds,
                    elementAnnotationMetadataFactory,
                    visitorContext,
                    typeArgument.variance == Variance.STAR
                )
            }

            // List<String>
            else -> {
                resolveTypeArgumentType(owner, typeArgument, parentTypeArguments, visitedTypes)
            }
        }
    }

    private fun resolveLowerBound(
        owner: KotlinNativeElement,
        typeArgument: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>,
        stripTypeArguments: Boolean,
    ): ClassElement? {
        return if (typeArgument.variance == Variance.CONTRAVARIANT) {
            resolveTypeArgumentType(
                owner,
                typeArgument,
                parentTypeArguments,
                visitedTypes,
                stripTypeArguments
            )
        } else {
            null
        }
    }

    private fun resolveUpperBound(
        owner: KotlinNativeElement,
        typeArgument: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement> = emptyMap(),
        visitedTypes: MutableSet<Any>,
        stripTypeArguments: Boolean
    ): ClassElement {
        return when (typeArgument.variance) {
            Variance.COVARIANT, Variance.STAR -> {
                resolveTypeArgumentType(
                    owner,
                    typeArgument,
                    parentTypeArguments,
                    visitedTypes,
                    stripTypeArguments
                )
            }

            else -> {
                val objectType =
                    visitorContext.resolver.getClassDeclarationByName(Object::class.java.name)!!
                newKotlinClassElement(objectType, parentTypeArguments, visitedTypes)
            }
        }
    }

    protected fun newKotlinClassElement(
        declaration: KSClassDeclaration,
        parentTypeArguments: Map<String, ClassElement> = emptyMap(),
        visitedTypes: MutableSet<Any> = HashSet(),
        stripTypeArguments: Boolean = false,
    ) = newClassElement(
        null,
        null,
        declaration,
        parentTypeArguments,
        visitedTypes,
        false,
        stripTypeArguments
    ) as KotlinClassElement

    protected fun newClassElement(
        declaration: KSClassDeclaration,
        parentTypeArguments: Map<String, ClassElement> = emptyMap(),
        visitedTypes: MutableSet<Any> = HashSet(),
        stripTypeArguments: Boolean = false,
    ) = newClassElement(
        null,
        null,
        declaration,
        parentTypeArguments,
        visitedTypes,
        true,
        stripTypeArguments
    )

    private fun newKotlinClassElement(
        owner: KotlinNativeElement?,
        type: KSType,
        parentTypeArguments: Map<String, ClassElement> = emptyMap(),
        visitedTypes: MutableSet<Any> = HashSet(),
        stripTypeArguments: Boolean = false,
    ) = newClassElement(
        owner,
        type,
        type.declaration.getClassDeclaration(visitorContext),
        parentTypeArguments,
        visitedTypes,
        false,
        stripTypeArguments
    ) as KotlinClassElement

    private fun resolveTypeArgumentType(
        owner: KotlinNativeElement,
        typeArgument: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any> = HashSet(),
        stripTypeArguments: Boolean = false
    ): ClassElement {
        val type = typeArgument.type
        val resolvedType = type!!.resolve()
        val stripTypeArguments2 = stripTypeArguments || !visitedTypes.add(type)

        val resolved = newTypeArgument(
            owner,
            resolvedType,
            parentTypeArguments,
            visitedTypes,
            stripTypeArguments2
        )
        if (resolved !is KotlinClassElement || resolved.isGenericPlaceholder) {
            return resolved
        }

        return KotlinTypeArgumentElement(
            KotlinTypeArgumentNativeElement(typeArgument, owner),
            resolved,
            visitorContext
        )
    }

    protected fun newClassElement(
        owner: KotlinNativeElement?,
        type: KSType,
        parentTypeArguments: Map<String, ClassElement> = emptyMap()
    ) = newClassElement(
        owner,
        type,
        type.declaration.getClassDeclaration(visitorContext),
        parentTypeArguments,
        HashSet()
    )

    private fun newTypeArgument(
        owner: KotlinNativeElement?,
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any> = HashSet(),
        stripTypeArguments: Boolean = false,
    ) = newClassElement(
        owner,
        type,
        type.declaration.getClassDeclaration(visitorContext),
        parentTypeArguments,
        visitedTypes,
        false,
        stripTypeArguments
    )

    private fun newClassElement(
        owner: KotlinNativeElement?,
        type: KSType?,
        declaration: KSClassDeclaration,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>,
        allowPrimitive: Boolean = true,
        stripTypeArguments: Boolean = false
    ): ClassElement {
        if (type != null) {
            val typeDeclaration = type.declaration
            if (typeDeclaration is KSTypeParameter) {
                return resolveTypeParameter(
                    owner!!,
                    typeDeclaration,
                    parentTypeArguments,
                    visitedTypes
                )
            }
        }
        val qualifiedName = declaration.qualifiedName
        if (qualifiedName != null) {
            val qualifiedNameString = qualifiedName.asString()
            val primitiveArray = primitiveArrays[qualifiedNameString]
            if (primitiveArray != null) {
                return primitiveArray
            }
            val canBePrimitive =
                type == null || type.annotations.toList().isEmpty() && !type.isMarkedNullable
            if (allowPrimitive && canBePrimitive && type?.nullability != Nullability.PLATFORM) {
                val element = primitives[qualifiedNameString]
                if (element != null) {
                    return element
                }
            }
            if (type != null && qualifiedNameString == "kotlin.Array") {
                val component = type.arguments[0].type!!.resolve()
                return newTypeArgument(
                    owner,
                    component,
                    parentTypeArguments,
                    visitedTypes,
                    false
                ).toArray()
            }
        }
        val typeArguments = if (stripTypeArguments) {
            resolveEmptyTypeArguments(declaration)
        } else if (type == null) {
            resolveTypeArguments(
                nativeType,
                declaration,
                parentTypeArguments,
                visitedTypes
            )
        } else {
            resolveTypeArguments(
                nativeType,
                type,
                parentTypeArguments,
                visitedTypes
            )
        }
        return if (declaration.classKind == ClassKind.ENUM_CLASS) {
            KotlinEnumElement(
                KotlinClassNativeElement(declaration, type, owner),
                elementAnnotationMetadataFactory,
                visitorContext,
                typeArguments
            )
        } else {
            KotlinClassElement(
                KotlinClassNativeElement(declaration, type, owner),
                elementAnnotationMetadataFactory,
                typeArguments,
                visitorContext
            )
        }
    }

    override fun toString(): String {
        return getDescription(false)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractKotlinElement<*>) return false
        if (isAnyOrObject() && other.isAnyOrObject()) return true
        if (nativeType.element != other.nativeType.element) return false
        return true
    }

    private fun isAnyOrObject(): Boolean {
        return name.equals(Object::class.java.name) || name.equals(Any::class.java.name)
    }

    override fun hashCode(): Int {
        return nativeType.element.hashCode()
    }

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

}
