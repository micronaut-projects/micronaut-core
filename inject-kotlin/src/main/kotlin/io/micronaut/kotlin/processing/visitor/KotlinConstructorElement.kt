package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ParameterElement

class KotlinConstructorElement(method: KSFunctionDeclaration,
                               declaringType: ClassElement,
                               annotationMetadata: AnnotationMetadata,
                               visitorContext: KotlinVisitorContext,
                               returnType: ClassElement,
                               parameters: List<ParameterElement>
): ConstructorElement, KotlinMethodElement(method, declaringType, returnType, returnType, parameters, annotationMetadata, visitorContext) {

    override fun getName() = "<init>"

    override fun getReturnType(): ClassElement = declaringType
}
