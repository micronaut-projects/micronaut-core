package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*

class KotlinElementFactory(private val visitorContext: KotlinVisitorContext): ElementFactory<KSDeclaration, KSClassDeclaration, KSFunctionDeclaration, KSPropertyDeclaration> {

    override fun newClassElement(type: KSClassDeclaration, annotationMetadata: AnnotationMetadata): ClassElement {
        if (type.qualifiedName!!.asString() == "kotlin.Array") {
            val component = type.typeParameters[0].bounds.first()
            val componentElement = newClassElement(component.resolve().declaration as KSClassDeclaration, annotationMetadata)
            return componentElement.toArray()
        }
        return KotlinClassElement(type, annotationMetadata, visitorContext)
    }

    override fun newClassElement(
        type: KSClassDeclaration,
        annotationMetadata: AnnotationMetadata,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        if (type.qualifiedName!!.asString() == "kotlin.Array") {
            val component = type.typeParameters[0].bounds.first()
            val componentElement = newClassElement(component.resolve().declaration as KSClassDeclaration, annotationMetadata)
            return componentElement.toArray()
        }
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
        declaringClass: ClassElement,
        method: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val returnType = method.returnType!!.resolve().declaration as KSClassDeclaration
        return KotlinMethodElement(
            method,
            declaringClass,
            newClassElement(returnType, annotationUtils.getAnnotationMetadata(returnType)),
            method.parameters.map { param ->
                KotlinParameterElement(declaringClass, param, annotationUtils.getAnnotationMetadata(param), visitorContext)
            },
            annotationMetadata,
            visitorContext)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertyGetter,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val returnType = method.returnType!!.resolve().declaration as KSClassDeclaration
        return KotlinMethodElement(method, declaringClass, newClassElement(returnType, annotationUtils.getAnnotationMetadata(returnType)), annotationMetadata, visitorContext)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertySetter,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        return KotlinMethodElement(method, declaringClass, annotationMetadata, visitorContext, KotlinParameterElement(declaringClass, method.parameter, annotationUtils.getAnnotationMetadata(method.parameter), visitorContext))
    }

    override fun newConstructorElement(
        declaringClass: ClassElement,
        constructor: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): ConstructorElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        return KotlinConstructorElement(constructor, declaringClass, annotationMetadata, visitorContext, declaringClass, constructor.parameters.map { param ->
            KotlinParameterElement(declaringClass, param, annotationUtils.getAnnotationMetadata(param), visitorContext)
        })
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
