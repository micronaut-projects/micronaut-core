package io.micronaut.kotlin.processing

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.visitor.VisitorContext
import java.lang.annotation.RetentionPolicy
import java.util.*

class KotlinAnnotationMetadataBuilder: AbstractAnnotationMetadataBuilder<KSDeclaration, KSAnnotation>() {

    override fun isMethodOrClassElement(element: KSDeclaration?): Boolean {
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
        TODO("Not yet implemented")
    }

    override fun hasAnnotation(element: KSDeclaration?, annotation: Class<out Annotation>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasAnnotation(element: KSDeclaration?, annotation: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasAnnotations(element: KSDeclaration?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getAnnotationTypeName(annotationMirror: KSAnnotation?): String {
        TODO("Not yet implemented")
    }

    override fun getElementName(element: KSDeclaration?): String {
        TODO("Not yet implemented")
    }

    override fun getAnnotationsForType(element: KSDeclaration?): MutableList<out KSAnnotation> {
        TODO("Not yet implemented")
    }

    override fun buildHierarchy(
        element: KSDeclaration?,
        inheritTypeAnnotations: Boolean,
        declaredOnly: Boolean
    ): MutableList<KSDeclaration> {
        TODO("Not yet implemented")
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

    override fun readAnnotationDefaultValues(annotationMirror: KSAnnotation?): MutableMap<out KSDeclaration, *> {
        TODO("Not yet implemented")
    }

    override fun readAnnotationDefaultValues(
        annotationName: String?,
        annotationType: KSDeclaration?
    ): MutableMap<out KSDeclaration, *> {
        TODO("Not yet implemented")
    }

    override fun readAnnotationRawValues(annotationMirror: KSAnnotation?): MutableMap<out KSDeclaration, *> {
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
        TODO("Not yet implemented")
    }

    override fun isInheritedAnnotation(annotationMirror: KSAnnotation): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInheritedAnnotationType(annotationType: KSDeclaration): Boolean {
        TODO("Not yet implemented")
    }
}
