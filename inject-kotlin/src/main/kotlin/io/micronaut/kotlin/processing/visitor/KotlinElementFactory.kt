package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*
import io.micronaut.kotlin.processing.AnnotationUtils

class KotlinElementFactory(private val visitorContext: KotlinVisitorContext): ElementFactory<KSDeclaration, KSClassDeclaration, KSFunctionDeclaration, KSPropertyDeclaration> {

    override fun newClassElement(type: KSClassDeclaration, annotationMetadata: AnnotationMetadata): ClassElement {
        TODO("Not yet implemented")
    }

    override fun newClassElement(
        type: KSClassDeclaration,
        annotationMetadata: AnnotationMetadata,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        return KotlinClassElement(type, annotationMetadata, visitorContext)
    }

    override fun newSourceClassElement(type: KSClassDeclaration, annotationMetadata: AnnotationMetadata): ClassElement {
        TODO("Not yet implemented")
    }

    override fun newSourceMethodElement(
        declaringClass: ClassElement?,
        method: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        TODO("Not yet implemented")
    }

    override fun newMethodElement(
        declaringClass: ClassElement?,
        method: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        TODO("Not yet implemented")
    }

    fun newMethodElement(
        declaringClass: ClassElement?,
        method: KSPropertyGetter,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        TODO("Not yet implemented")
    }

    fun newMethodElement(
        declaringClass: ClassElement?,
        method: KSPropertySetter,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        TODO("Not yet implemented")
    }

    override fun newConstructorElement(
        declaringClass: ClassElement?,
        constructor: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): ConstructorElement {
        TODO("Not yet implemented")
    }

    override fun newFieldElement(
        declaringClass: ClassElement?,
        field: KSPropertyDeclaration,
        annotationMetadata: AnnotationMetadata
    ): FieldElement {
        TODO("Not yet implemented")
    }

    override fun newFieldElement(field: KSPropertyDeclaration, annotationMetadata: AnnotationMetadata): FieldElement {
        TODO("Not yet implemented")
    }
}
