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
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getVisibility
import io.micronaut.kotlin.processing.kspNode
import java.util.*

@OptIn(KspExperimental::class)
open class KotlinMethodElement: AbstractKotlinElement<KSAnnotated>, MethodElement {

    private val name: String
    private val owningType: ClassElement
    private val internalDeclaringType: ClassElement by lazy {
        var parent = declaration.parent
        if (parent is KSPropertyDeclaration) {
            parent = parent.parent
        }
        if (parent is KSClassDeclaration) {
            visitorContext.elementFactory.newClassElement(
                parent.asStarProjectedType()
            )
        } else {
            // shouldn't happen
            visitorContext.getClassElement(Object::class.java.name).get()
        }
    }

    private val parameters: List<ParameterElement>
    private val returnType: ClassElement
    private val abstract: Boolean
    private val public: Boolean
    private val private: Boolean
    private val protected: Boolean
    private val internal: Boolean

    constructor(propertyType : ClassElement,
                method: KSPropertySetter,
                owningType: ClassElement,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext
    ) : super(KSPropertySetterReference(method), elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.owningType = owningType
        this.returnType = PrimitiveElement.VOID
        this.abstract = method.receiver.isAbstract()
        val visibility = method.getVisibility()
        this.public = visibility == Visibility.PUBLIC
        this.private = visibility == Visibility.PRIVATE
        this.protected = visibility == Visibility.PROTECTED
        this.internal = visibility == Visibility.INTERNAL
        this.parameters = listOf(KotlinParameterElement(
            propertyType, this, method.parameter, elementAnnotationMetadataFactory, visitorContext
        ))
    }

    constructor(method: KSPropertyGetter,
                owningType: ClassElement,
                returnType: ClassElement,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext,
    ) : super(KSPropertyGetterReference(method), elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.owningType = owningType
        this.parameters = emptyList()
        this.returnType = returnType
        this.abstract = method.receiver.isAbstract()
        this.public = method.receiver.isPublic()
        this.private = method.receiver.isPrivate()
        this.protected = method.receiver.isProtected()
        this.internal = method.receiver.isInternal()
    }

    constructor(method: KSFunctionDeclaration,
                owningType: ClassElement,
                returnType: ClassElement,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext
    ) : super(KSFunctionReference(method), elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.owningType = owningType
        this.parameters = method.parameters.map {
            val t = visitorContext.elementFactory.newClassElement(
                it.type.resolve(),
                elementAnnotationMetadataFactory)
            KotlinParameterElement(
                t,
                this,
                it,
                elementAnnotationMetadataFactory,
                visitorContext
            )
        }
        this.returnType = returnType
        this.abstract = method.isAbstract
        this.public = method.isPublic()
        this.private = method.isPrivate()
        this.protected = method.isProtected()
        this.internal = method.isInternal()
    }

    protected constructor(method: KSAnnotated,
                          name: String,
                          owningType: ClassElement,
                          elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                          visitorContext: KotlinVisitorContext,
                          returnType: ClassElement,
                          parameters: List<ParameterElement>,
                          abstract: Boolean,
                          public: Boolean,
                          private: Boolean,
                          protected: Boolean,
                          internal: Boolean
    ) : super(method, elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.owningType = owningType
        this.parameters = parameters
        this.returnType = returnType
        this.abstract = abstract
        this.public = public
        this.private = private
        this.protected = protected
        this.internal = internal
    }

    override fun getOwningType(): ClassElement {
        return owningType
    }

    override fun isSynthetic(): Boolean {
        return if (declaration is KSPropertyGetter || declaration is KSPropertySetter) {
            return true
        } else {
            if (declaration is KSFunctionDeclaration) {
                return declaration.functionKind != FunctionKind.MEMBER && declaration.functionKind != FunctionKind.STATIC
            } else {
                return false
            }
        }
    }

    override fun isFinal(): Boolean {
        return if (declaration is KSPropertyGetter || declaration is KSPropertySetter) {
            true
        } else  {
            super<AbstractKotlinElement>.isFinal()
        }
    }

    override fun getModifiers(): MutableSet<ElementModifier> {
        return super<AbstractKotlinElement>.getModifiers()
    }

    override fun getDeclaredTypeVariables(): MutableList<out GenericPlaceholderElement> {
        val nativeType = kspNode()
        return if (nativeType is KSDeclaration) {
            nativeType.typeParameters.map {
                KotlinGenericPlaceholderElement(it, annotationMetadataFactory, visitorContext)
            }.toMutableList()
        } else {
            super.getDeclaredTypeVariables()
        }
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
        // TODO: suspend
        return super.getSuspendParameters()
    }

    override fun overrides(overridden: MethodElement): Boolean {
        val nativeType = kspNode()
        val overriddenNativeType = overridden.kspNode()
        if (nativeType == overriddenNativeType) {
            return false
        } else if (nativeType is KSFunctionDeclaration) {
            return overriddenNativeType == nativeType.findOverridee()
        } else if (nativeType is KSPropertySetter && overriddenNativeType is KSPropertySetter) {
            return overriddenNativeType.receiver == nativeType.receiver.findOverridee()
        }
        return false
    }

    override fun hides(memberElement: MemberElement?): Boolean {
        // not sure how to implement this correctly for Kotlin
        return false
    }

    override fun withNewOwningType(owningType: ClassElement): MethodElement {
        val newMethod = KotlinMethodElement(
            declaration,
            name,
            owningType as KotlinClassElement,
            annotationMetadataFactory,
            visitorContext,
            returnType,
            parameters,
            abstract,
            public,
            private,
            protected,
            internal
        )
        copyValues(newMethod)
        return newMethod
    }

    override fun getName(): String {
        return name
    }

    override fun getDeclaringType(): ClassElement {
        return internalDeclaringType
    }

    override fun getReturnType(): ClassElement {
        return returnType
    }

    override fun getGenericReturnType(): ClassElement {
        return resolveGeneric(declaration.parent, returnType, owningType, visitorContext)
    }

    override fun getParameters(): Array<ParameterElement> {
        return parameters.toTypedArray()
    }

    override fun isAbstract(): Boolean = abstract

    override fun isPublic(): Boolean = public

    override fun isProtected(): Boolean = protected
    override fun copyThis(): KotlinMethodElement {
        return KotlinMethodElement(
            declaration,
            name,
            owningType,
            annotationMetadataFactory,
            visitorContext,
            returnType,
            parameters,
            abstract,
            public,
            private,
            protected,
            internal
        )
    }

    override fun isPrivate(): Boolean = private
    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): MethodElement {
        return super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as MethodElement
    }

    override fun toString(): String {
        return "$simpleName(" + parameters.joinToString(",") {
            if (it.type.isGenericPlaceholder) {
                (it.type as GenericPlaceholderElement).variableName
            } else {
                it.genericType.name
            }
        } + ")"
    }

    override fun withParameters(vararg newParameters: ParameterElement): MethodElement {
        return KotlinMethodElement(declaration, name, owningType, annotationMetadataFactory, visitorContext, returnType, newParameters.toList(), abstract, public, private, protected, internal)
    }

    override fun getThrownTypes(): Array<ClassElement> {
        return emptyArray() // Kotlin doesn't support throws declarations
    }

}
