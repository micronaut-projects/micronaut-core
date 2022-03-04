package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.*
import io.micronaut.kotlin.processing.isTypeReference

class KotlinElementFactory(private val visitorContext: KotlinVisitorContext): ElementFactory<Any, KSType, KSFunctionDeclaration, KSPropertyDeclaration> {

    companion object {
        val primitives = mapOf(
            "kotlin.Boolean" to PrimitiveElement.BOOLEAN,
            "kotlin.Char" to PrimitiveElement.CHAR,
            "kotlin.Short" to PrimitiveElement.SHORT,
            "kotlin.Int" to PrimitiveElement.INT,
            "kotlin.Long" to PrimitiveElement.LONG,
            "kotlin.Float" to PrimitiveElement.FLOAT,
            "kotlin.Double" to PrimitiveElement.DOUBLE,
            "kotlin.Byte" to PrimitiveElement.BYTE,
            "kotlin.Unit" to PrimitiveElement.VOID
        )
        val primitiveArrays = mapOf(
            "kotlin.BooleanArray" to PrimitiveElement.BOOLEAN.toArray(),
            "kotlin.CharArray" to PrimitiveElement.CHAR.toArray(),
            "kotlin.ShortArray" to PrimitiveElement.SHORT.toArray(),
            "kotlin.IntArray" to PrimitiveElement.INT.toArray(),
            "kotlin.LongArray" to PrimitiveElement.LONG.toArray(),
            "kotlin.FloatArray" to PrimitiveElement.FLOAT.toArray(),
            "kotlin.DoubleArray" to PrimitiveElement.DOUBLE.toArray(),
            "kotlin.ByteArray" to PrimitiveElement.BYTE.toArray(),
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
        return newClassElement(type, annotationMetadata, emptyMap(), !typeVariable)
    }

    override fun newClassElement(
        type: KSType,
        annotationMetadata: AnnotationMetadata,
        resolvedGenerics: Map<String, ClassElement>
    ): ClassElement {
        return newClassElement(type, annotationMetadata, resolvedGenerics, true)
    }

    fun newClassElement(type: KSType,
                        annotationMetadata: AnnotationMetadata,
                        resolvedGenerics: Map<String, ClassElement>,
                        allowPrimitive: Boolean): ClassElement {
        val declaration = type.declaration
        val qualifiedName = declaration.qualifiedName!!.asString()
        var element = primitiveArrays[qualifiedName]
        if (element != null) {
            return element
        }
        if (qualifiedName == "kotlin.Array") {
            val component = type.arguments[0].type!!.resolve()
            val componentElement = newClassElement(component, annotationMetadata, resolvedGenerics, false)
            return componentElement.toArray()
        } else if (declaration is KSTypeParameter) {
            val name = declaration.name.asString()
            return if (resolvedGenerics.containsKey(name)) {
                resolvedGenerics[name]!!
            } else {
                KotlinGenericPlaceholderElement(declaration, annotationMetadata, visitorContext)
            }
        }
        if (allowPrimitive && !type.isMarkedNullable) {
            element = primitives[qualifiedName]
            if (element != null) {
                return element
            }
        }
        return if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
            KotlinEnumElement(type, annotationMetadata, visitorContext)
        } else {
            KotlinClassElement(type, annotationMetadata, visitorContext)
        }
    }

    fun newPropertyElement(declaringClass: ClassElement, propertyDeclaration: KSPropertyDeclaration): PropertyElement {
        val type = propertyDeclaration.type.resolve()
        val parents = mutableListOf<KSAnnotated>()
        if (propertyDeclaration.getter != null) {
            parents.add(propertyDeclaration.getter!!)
        }
        if (propertyDeclaration.setter != null) {
            parents.add(propertyDeclaration.setter!!)
        }
        val annotationMetadata = if (parents.isNotEmpty()) {
            visitorContext.getAnnotationUtils().getAnnotationMetadata(parents, propertyDeclaration)
        } else {
            visitorContext.getAnnotationUtils().getAnnotationMetadata(propertyDeclaration)
        }
        return KotlinPropertyElement(
            declaringClass,
            newClassElement(
                type,
                visitorContext.getAnnotationUtils().getAnnotationMetadata(type.declaration),
                declaringClass.typeArguments,
                !propertyDeclaration.isTypeReference()),
            propertyDeclaration,
            annotationMetadata,
            visitorContext
        )
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
        return newMethodElement(declaringClass, method, annotationMetadata, declaringClass.typeArguments)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSFunctionDeclaration,
        annotationMetadata: AnnotationMetadata,
        typeArguments: Map<String, ClassElement>
    ): KotlinMethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val returnType = method.returnType!!.resolve()

        val allTypeArguments = mutableMapOf<String, ClassElement>()
        allTypeArguments.putAll(typeArguments)
        method.typeParameters.forEach {
            allTypeArguments[it.name.asString()] = KotlinGenericPlaceholderElement(it, annotationUtils.getAnnotationMetadata(it), visitorContext)
        }

        return KotlinMethodElement(
            method,
            declaringClass,
            newClassElement(returnType, annotationUtils.newAnnotationBuilder().buildDeclared(returnType.declaration, returnType.annotations.toList(), false), allTypeArguments),
            method.parameters.map { param ->
                KotlinParameterElement(newClassElement(param.type.resolve(), allTypeArguments), param, annotationUtils.getAnnotationMetadata(param), visitorContext)
            },
            annotationMetadata,
            visitorContext)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertyGetter,
        type: ClassElement,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        return KotlinMethodElement(method, declaringClass, type, annotationMetadata, visitorContext)
    }

    fun newMethodElement(
        declaringClass: ClassElement,
        method: KSPropertySetter,
        type: ClassElement,
        annotationMetadata: AnnotationMetadata
    ): MethodElement {
        val annotationUtils = visitorContext.getAnnotationUtils()
        return KotlinMethodElement(method, declaringClass, annotationMetadata, visitorContext, KotlinParameterElement(type, method.parameter, AnnotationMetadataHierarchy(annotationMetadata, annotationUtils.getAnnotationMetadata(method.parameter)), visitorContext))
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
