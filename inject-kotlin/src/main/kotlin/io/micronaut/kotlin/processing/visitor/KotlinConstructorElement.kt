package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ParameterElement

class KotlinConstructorElement(method: KSDeclaration,
                               declaringType: ClassElement,
                               annotationMetadata: AnnotationMetadata,
                               visitorContext: KotlinVisitorContext,
                               returnType: ClassElement,
                               parameters: List<ParameterElement>
): ConstructorElement, KotlinMethodElement(method, "<init>", declaringType, annotationMetadata, visitorContext, returnType, parameters) {

    override fun getName() = "<init>"

    override fun getReturnType(): ClassElement = declaringType
}
