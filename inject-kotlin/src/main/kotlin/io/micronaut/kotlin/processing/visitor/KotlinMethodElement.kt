package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.ast.PrimitiveElement

@OptIn(KspExperimental::class)
open class KotlinMethodElement: AbstractKotlinElement<KSDeclaration>, MethodElement {

    private val name: String
    private val declaringType: ClassElement
    private val parameters: List<ParameterElement>
    private val returnType: ClassElement
    private val abstract: Boolean

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
        this.abstract = method.modifiers.contains(Modifier.ABSTRACT)
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
        this.abstract = method.modifiers.contains(Modifier.ABSTRACT)
    }

    constructor(method: KSFunctionDeclaration,
                declaringType: ClassElement,
                returnType: ClassElement,
                parameters: List<ParameterElement>,
                annotationMetadata: AnnotationMetadata,
                visitorContext: KotlinVisitorContext,
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.declaringType = declaringType
        this.parameters = parameters
        this.returnType = returnType
        this.abstract = method.isAbstract
    }

    protected constructor(method: KSDeclaration,
                          name: String,
                          declaringType: ClassElement,
                          annotationMetadata: AnnotationMetadata,
                          visitorContext: KotlinVisitorContext,
                          returnType: ClassElement,
                          parameters: List<ParameterElement>,
                          abstract: Boolean
    ) : super(method, annotationMetadata, visitorContext) {
        this.name = name
        this.declaringType = declaringType
        this.parameters = parameters
        this.returnType = returnType
        this.abstract = abstract
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

    override fun isAbstract(): Boolean = abstract

    override fun withNewParameters(vararg newParameters: ParameterElement): MethodElement {
        return KotlinMethodElement(declaration, name, declaringType, annotationMetadata, visitorContext, returnType, newParameters.toList(), abstract)
    }

    override fun withNewMetadata(annotationMetadata: AnnotationMetadata): MethodElement {
        return KotlinMethodElement(declaration, name, declaringType, annotationMetadata, visitorContext, returnType, parameters, abstract)
    }

}
