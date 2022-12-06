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
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getVisibility

@OptIn(KspExperimental::class)
open class KotlinMethodElement: AbstractKotlinElement<KSDeclaration>, MethodElement {

    private val name: String
    private val declaringType: ClassElement
    private val parameters: List<ParameterElement>
    private val returnType: ClassElement
    private val genericReturnType: ClassElement
    private val abstract: Boolean
    private val public: Boolean
    private val private: Boolean
    private val protected: Boolean
    private val internal: Boolean

    constructor(propertyType : ClassElement,
                method: KSPropertySetter,
                declaringType: ClassElement,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext
    ) : super(method.receiver, elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.returnType = PrimitiveElement.VOID
        this.genericReturnType = PrimitiveElement.VOID
        this.abstract = method.receiver.isAbstract()
        val visibility = method.getVisibility()
        this.public = visibility == Visibility.PUBLIC
        this.private = visibility == Visibility.PRIVATE
        this.protected = visibility == Visibility.PROTECTED
        this.internal = visibility == Visibility.INTERNAL
        this.parameters = listOf(KotlinParameterElement(
            propertyType, propertyType, this, method.parameter, elementAnnotationMetadataFactory, visitorContext
        ))
    }

    constructor(method: KSPropertyGetter,
                declaringType: ClassElement,
                returnType: ClassElement,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext,
    ) : super(method.receiver, elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = emptyList()
        this.returnType = returnType
        this.genericReturnType = returnType
        this.abstract = method.receiver.isAbstract()
        this.public = method.receiver.isPublic()
        this.private = method.receiver.isPrivate()
        this.protected = method.receiver.isProtected()
        this.internal = method.receiver.isInternal()
    }

    constructor(method: KSFunctionDeclaration,
                declaringType: ClassElement,
                returnType: ClassElement,
                genericReturnType: ClassElement,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext,
                typeArguments: Map<String, ClassElement>
    ) : super(method, elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = method.parameters.map {
            val t =
                visitorContext.elementFactory.newClassElement(it.type.resolve(), elementAnnotationMetadataFactory, typeArguments)
            KotlinParameterElement(
                t,
                t,
                this,
                it,
                elementAnnotationMetadataFactory,
                visitorContext
            )
        }
        this.returnType = returnType
        this.genericReturnType = genericReturnType
        this.abstract = method.isAbstract
        this.public = method.isPublic()
        this.private = method.isPrivate()
        this.protected = method.isProtected()
        this.internal = method.isInternal()
    }

    protected constructor(method: KSDeclaration,
                          name: String,
                          declaringType: ClassElement,
                          elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                          visitorContext: KotlinVisitorContext,
                          returnType: ClassElement,
                          genericReturnType: ClassElement,
                          parameters: List<ParameterElement>,
                          abstract: Boolean,
                          public: Boolean,
                          private: Boolean,
                          protected: Boolean,
                          internal: Boolean
    ) : super(method, elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.declaringType = declaringType
        this.parameters = parameters
        this.returnType = returnType
        this.genericReturnType = genericReturnType
        this.abstract = abstract
        this.public = public
        this.private = private
        this.protected = protected
        this.internal = internal
    }

    override fun withNewOwningType(owningType: ClassElement): MethodElement {
        var newMethod = KotlinMethodElement(
            declaration,
            name,
            owningType,
            annotationMetadataFactory,
            visitorContext,
            returnType,
            genericReturnType,
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
        return declaringType
    }

    override fun getReturnType(): ClassElement {
        return returnType
    }

    override fun getGenericReturnType(): ClassElement {
        return genericReturnType
    }

    override fun getParameters(): Array<ParameterElement> {
        return parameters.toTypedArray()
    }

    override fun isAbstract(): Boolean = abstract

    override fun isPublic(): Boolean = public

    override fun isProtected(): Boolean = protected
    override fun copyThis(): AbstractKotlinElement<KSDeclaration> {
        return KotlinMethodElement(
            declaration,
            name,
            declaringType,
            annotationMetadataFactory,
            visitorContext,
            returnType,
            genericReturnType,
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
        return KotlinMethodElement(declaration, name, declaringType, annotationMetadataFactory, visitorContext, returnType, genericReturnType, newParameters.toList(), abstract, public, private, protected, internal)
    }

}
