package io.micronaut.kotlin.processing

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.processing.JavaModelUtils
import io.micronaut.inject.visitor.VisitorContext
import java.lang.annotation.RetentionPolicy
import java.util.*
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Types

class KotlinAnnotationMetadataBuilder: AbstractAnnotationMetadataBuilder<KSDeclaration, KSAnnotation>() {

    override fun isMethodOrClassElement(element: KSDeclaration): Boolean {
        return element is KSClassDeclaration || element is KSFunctionDeclaration
    }

    override fun getDeclaringType(element: KSDeclaration): String {
        val closestClassDeclaration = element.closestClassDeclaration()
        if (closestClassDeclaration != null) {
            return closestClassDeclaration.qualifiedName!!.asString()
        }
        return element.simpleName.asString()
    }

    override fun getTypeForAnnotation(annotationMirror: KSAnnotation): KSDeclaration {
        return annotationMirror.annotationType.resolve().declaration
    }

    override fun hasAnnotation(element: KSDeclaration, annotation: Class<out Annotation>): Boolean {
        return hasAnnotation(element, annotation.name)
    }

    override fun hasAnnotation(element: KSDeclaration, annotation: String): Boolean {
        return element.annotations.map {
            it.annotationType.resolve().declaration.qualifiedName
        }.any {
            it?.asString() == annotation
        }
    }

    override fun hasAnnotations(element: KSDeclaration): Boolean {
        return element.annotations.iterator().hasNext()
    }

    override fun getAnnotationTypeName(annotationMirror: KSAnnotation): String {
        return getTypeForAnnotation(annotationMirror).qualifiedName!!.asString()
    }

    override fun getElementName(element: KSDeclaration): String {
        return if (element is KSClassDeclaration) {
            element.qualifiedName!!.asString()
        } else {
            element.simpleName.asString()
        }
    }

    override fun getAnnotationsForType(element: KSDeclaration): MutableList<out KSAnnotation> {
        return element.annotations.toMutableList()
    }

    override fun buildHierarchy(
        element: KSDeclaration,
        inheritTypeAnnotations: Boolean,
        declaredOnly: Boolean
    ): MutableList<KSDeclaration> {
        if (declaredOnly) {
            return mutableListOf(element)
        }
        if (element is KSClassDeclaration) {
            val hierarchy = mutableListOf<KSDeclaration>()
            hierarchy.add(element)
            if (element.classKind == ClassKind.ANNOTATION_CLASS) {
                return hierarchy
            }
            populateTypeHierarchy(element, hierarchy)
            hierarchy.reverse()
            return hierarchy
        } else {
            return mutableListOf(element)
        }
    }

    override fun readAnnotationRawValues(
        originatingElement: KSDeclaration?,
        annotationName: String?,
        member: KSDeclaration?,
        memberName: String?,
        annotationValue: Any?,
        annotationValues: MutableMap<CharSequence, Any>?
    ) {
        TODO("Not yet implemented")
    }

    override fun isValidationRequired(member: KSDeclaration?): Boolean {
        TODO("Not yet implemented")
    }

    override fun addError(originatingElement: KSDeclaration, error: String) {
        TODO("Not yet implemented")
    }

    override fun addWarning(originatingElement: KSDeclaration, warning: String) {
        TODO("Not yet implemented")
    }

    override fun readAnnotationValue(
        originatingElement: KSDeclaration?,
        member: KSDeclaration?,
        memberName: String?,
        annotationValue: Any?
    ): Any {
        TODO("Not yet implemented")
    }

    override fun readAnnotationDefaultValues(annotationMirror: KSAnnotation): MutableMap<out KSDeclaration, *> {
        return mutableMapOf<KSDeclaration, Any>()
    }

    override fun readAnnotationDefaultValues(
        annotationName: String,
        annotationType: KSDeclaration
    ): MutableMap<out KSDeclaration, *> {
        return mutableMapOf<KSDeclaration, Any>()
    }

    override fun readAnnotationRawValues(annotationMirror: KSAnnotation): MutableMap<out KSDeclaration, *> {
        TODO("Not yet implemented")
    }

    override fun getAnnotationValues(
        originatingElement: KSDeclaration?,
        member: KSDeclaration?,
        annotationType: Class<*>?
    ): OptionalValues<*> {
        TODO("Not yet implemented")
    }

    override fun getAnnotationMemberName(member: KSDeclaration?): String {
        TODO("Not yet implemented")
    }

    override fun getRepeatableName(annotationMirror: KSAnnotation?): String? {
        TODO("Not yet implemented")
    }

    override fun getRepeatableNameForType(annotationType: KSDeclaration?): String? {
        TODO("Not yet implemented")
    }

    override fun getAnnotationMirror(annotationName: String?): Optional<KSDeclaration> {
        TODO("Not yet implemented")
    }

    override fun getAnnotationMember(originatingElement: KSDeclaration?, member: CharSequence?): KSDeclaration? {
        TODO("Not yet implemented")
    }

    override fun createVisitorContext(): VisitorContext {
        TODO("Not yet implemented")
    }

    override fun getRetentionPolicy(annotation: KSDeclaration): RetentionPolicy {
        val retentionPolicy = annotation.annotations.find {
            getAnnotationTypeName(it) == RetentionPolicy::class.java.name
        }
        if (retentionPolicy == null) {
            return RetentionPolicy.RUNTIME
        } else {
            TODO("get value from annotation")
        }
    }

    override fun isInheritedAnnotation(annotationMirror: KSAnnotation): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInheritedAnnotationType(annotationType: KSDeclaration): Boolean {
        TODO("Not yet implemented")
    }

    private fun populateTypeHierarchy(element: KSClassDeclaration, hierarchy: MutableList<KSDeclaration>) {
        element.superTypes.forEach {
            val declaration = it.resolve().declaration
            hierarchy.add(declaration)
            populateTypeHierarchy(declaration as KSClassDeclaration, hierarchy)
        }
    }
}
