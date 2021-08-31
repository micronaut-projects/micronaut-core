package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*

class KotlinElementFactory: ElementFactory<KSDeclaration, KSClassDeclaration, KSFunctionDeclaration, KSPropertyDeclaration> {

    override fun newClassElement(type: KSClassDeclaration, annotationMetadata: AnnotationMetadata): ClassElement {
        TODO("Not yet implemented")
    }

    override fun newClassElement(
        type: KSClassDeclaration,
        annotationMetadata: AnnotationMetadata,
        resolvedGenerics: MutableMap<String, ClassElement>
    ): ClassElement {
        TODO("Not yet implemented")
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
