package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PrimitiveElement
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

    constructor(method: KSPropertySetter,
                declaringType: ClassElement,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
                parameter: ParameterElement
    ) : super(method.receiver, annotationMetadata, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = listOf(parameter)
        this.returnType = PrimitiveElement.VOID
        this.genericReturnType = PrimitiveElement.VOID
        this.abstract = method.receiver.isAbstract()
        val visibility = method.getVisibility()
        this.public = visibility == Visibility.PUBLIC
        this.private = visibility == Visibility.PRIVATE
        this.protected = visibility == Visibility.PROTECTED
        this.internal = visibility == Visibility.INTERNAL
    }

    constructor(method: KSPropertyGetter,
                declaringType: ClassElement,
                returnType: ClassElement,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
    ) : super(method.receiver, annotationMetadata, visitorContext) {
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
                parameters: List<ParameterElement>,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = parameters
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
                          annotationMetadata: AnnotationMetadata,
                          visitorContext: KotlinVisitorContext,
                          returnType: ClassElement,
                          genericReturnType: ClassElement,
                          parameters: List<ParameterElement>,
                          abstract: Boolean,
                          public: Boolean,
                          private: Boolean,
                          protected: Boolean,
                          internal: Boolean
    ) : super(method, annotationMetadata, visitorContext) {
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

    override fun isPrivate(): Boolean = private

    override fun isVisibleInPackage(packageName: String): Boolean {
        return super.isVisibleInPackage(packageName) || internal
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

    override fun withNewParameters(vararg newParameters: ParameterElement): MethodElement {
        return KotlinMethodElement(declaration, name, declaringType, annotationMetadata, visitorContext, returnType, genericReturnType, newParameters.toList(), abstract, public, private, protected, internal)
    }

    override fun withNewMetadata(annotationMetadata: AnnotationMetadata): MethodElement {
        return KotlinMethodElement(declaration, name, declaringType, annotationMetadata, visitorContext, returnType, genericReturnType, parameters, abstract, public, private, protected, internal)
    }

}
