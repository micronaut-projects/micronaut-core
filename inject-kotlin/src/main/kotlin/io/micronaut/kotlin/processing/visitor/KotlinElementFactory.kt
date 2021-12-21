package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*

class KotlinElementFactory(private val visitorContext: KotlinVisitorContext): ElementFactory<Any, KSType, KSFunctionDeclaration, KSPropertyDeclaration> {

    companion object {
        val primitives = mapOf(
            "kotlin.Boolean" to PrimitiveElement.BOOLEAN,
            "kotlin.Int" to PrimitiveElement.INT
        )
    }

    fun newClassElement(
        type: KSType
    ): ClassElement {
        return newClassElement(type, visitorContext.getAnnotationUtils().getAnnotationMetadata(type.declaration))
    }

    fun newClassElement(
        type: KSType,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        return newClassElement(type, visitorContext.getAnnotationUtils().getAnnotationMetadata(type.declaration), resolvedGenerics)
    }

    override fun newClassElement(type: KSType, annotationMetadata: AnnotationMetadata): ClassElement {
        return newClassElement(type, annotationMetadata, false)
    }

    private fun newClassElement(type: KSType, annotationMetadata: AnnotationMetadata, typeVariable: Boolean): ClassElement {
        return newClassElement(type, annotationMetadata, emptyMap(), typeVariable)
    }

    override fun newClassElement(
        type: KSType,
        annotationMetadata: AnnotationMetadata,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        return newClassElement(type, annotationMetadata, resolvedGenerics, false)
    }

    fun newClassElement(type: KSType,
                                annotationMetadata: AnnotationMetadata,
                                resolvedGenerics: Map<String, ClassElement>,
                                typeVariable: Boolean): ClassElement {
        val declaration = type.declaration
        val qualifiedName = declaration.qualifiedName!!.asString()
        if (qualifiedName == "kotlin.Array") {
            val component = type.arguments[0].type!!.resolve()
            val componentElement = newClassElement(component, annotationMetadata, resolvedGenerics)
            return componentElement.toArray()
        } else if (declaration is KSTypeParameter) {
            val name = declaration.name.asString()
            return if (resolvedGenerics.containsKey(name)) {
                resolvedGenerics[name]!!
            } else {
                KotlinGenericPlaceholderElement(declaration, annotationMetadata, visitorContext)
            }
        }
        if (!typeVariable) {
            val element = primitives[qualifiedName]
            if (element != null) {
                return element
            }
        }
        return KotlinClassElement(type, annotationMetadata, visitorContext)
    }

    override fun newSourceClassElement(type: KSType, annotationMetadata: AnnotationMetadata): ClassElement {
        TODO("Not yet implemented")
    }

    override fun newSourceMethodElement(
        declaringClass: ClassElement,
        method: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        TODO("Not yet implemented")
    }

    override fun newMethodElement(
        declaringClass: ClassElement,
        method: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): KotlinMethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val returnType = method.returnType!!.resolve()
        return KotlinMethodElement(
            method,
            declaringClass,
            newClassElement(returnType, annotationUtils.getAnnotationMetadata(returnType.declaration), declaringClass.typeArguments),
            method.parameters.map { param ->
                KotlinParameterElement(newClassElement(param.type.resolve()), param, annotationUtils.getAnnotationMetadata(param), visitorContext)
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
        val returnType = method.returnType!!.resolve()
        return KotlinMethodElement(method, declaringClass, newClassElement(returnType, annotationUtils.getAnnotationMetadata(returnType.declaration), declaringClass.typeArguments), annotationMetadata, visitorContext)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertySetter,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        return KotlinMethodElement(method, declaringClass, annotationMetadata, visitorContext, KotlinParameterElement(newClassElement(method.parameter.type.resolve(), declaringClass.typeArguments), method.parameter, annotationUtils.getAnnotationMetadata(method.parameter), visitorContext))
    }

    override fun newConstructorElement(
        declaringClass: ClassElement,
        constructor: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata
    ): ConstructorElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        return KotlinConstructorElement(constructor, declaringClass, annotationMetadata, visitorContext, declaringClass, constructor.parameters.map { param ->
            KotlinParameterElement(newClassElement(param.type.resolve(), declaringClass.typeArguments), param, annotationUtils.getAnnotationMetadata(param), visitorContext)
        })
    }

    override fun newFieldElement(
        declaringClass: ClassElement,
        field: KSPropertyDeclaration,
        annotationMetadata: AnnotationMetadata
    ): FieldElement {
        return KotlinFieldElement(field, declaringClass, annotationMetadata, visitorContext)
    }

    override fun newFieldElement(field: KSPropertyDeclaration, annotationMetadata: AnnotationMetadata): FieldElement {
        TODO("Not yet implemented")
    }
}
