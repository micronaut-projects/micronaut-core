package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PrimitiveElement

@OptIn(KspExperimental::class)
open class KotlinMethodElement: AbstractKotlinElement, MethodElement {

    private val name: String
    private val declaringType: ClassElement
    private val parameters: List<ParameterElement>
    private val returnType: ClassElement

    constructor(method: KSPropertySetter,
                declaringType: ClassElement,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
                parameter: ParameterElement
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = listOf(parameter)
        this.returnType = PrimitiveElement.VOID
    }

    constructor(method: KSPropertyGetter,
                declaringType: ClassElement,
                returnType: ClassElement,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = emptyList()
        this.returnType = returnType
    }

    constructor(method: KSFunctionDeclaration,
                declaringType: ClassElement,
                returnType: ClassElement,
                parameters: List<ParameterElement>,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = method.simpleName.asString()
        this.declaringType = declaringType
        this.parameters = parameters
        this.returnType = returnType
    }

    protected constructor(method: KSNode,
                        name: String,
                        declaringType: ClassElement,
                        annotationMetadata: AnnotationMetadata,
                        visitorContext: KotlinVisitorContext,
                        returnType: ClassElement,
                        parameters: List<ParameterElement>
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = name
        this.declaringType = declaringType
        this.parameters = parameters
        this.returnType = returnType
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

    override fun getParameters(): Array<ParameterElement> {
        return parameters.toTypedArray()
    }

    override fun withNewParameters(vararg newParameters: ParameterElement): MethodElement {
        return KotlinMethodElement(declaration, name, declaringType, annotationMetadata, visitorContext, returnType, newParameters.toList())
    }
}
