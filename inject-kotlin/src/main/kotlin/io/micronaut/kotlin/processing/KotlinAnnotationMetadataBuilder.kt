package io.micronaut.kotlin.processing

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.visitor.VisitorContext
import java.lang.annotation.RetentionPolicy
import java.util.*

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
        originatingElement: KSDeclaration,
        annotationName: String,
        member: KSDeclaration,
        memberName: String,
        annotationValue: Any,
        annotationValues: MutableMap<CharSequence, Any>
    ) {
        if (!annotationValues.containsKey(memberName)) {
            val value = readAnnotationValue(originatingElement, member, memberName, annotationValue)
            if (value != null) {
                validateAnnotationValue(originatingElement, annotationName, member, memberName, value)
                annotationValues[memberName] = value
            }
        }
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
        originatingElement: KSDeclaration,
        member: KSDeclaration,
        memberName: String,
        annotationValue: Any
    ): Any? {
        return annotationValue
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
        val map = mutableMapOf<KSDeclaration, Any>()
        val declaration = annotationMirror.annotationType.resolve().declaration as KSClassDeclaration
        declaration.getAllProperties().forEach { prop ->
            val argument = annotationMirror.arguments.find { it.name == prop.simpleName }
            if (argument?.value != null) {
                map[prop] = argument.value!!
            }
        }
        return map
    }

    override fun getAnnotationValues(
        originatingElement: KSDeclaration,
        member: KSDeclaration,
        annotationType: Class<*>
    ): OptionalValues<*> {
        val annotationMirrors: List<KSAnnotation> = member.annotations.toList()
        val annotationName = annotationType.name
        for (annotationMirror in annotationMirrors) {
            if (annotationMirror.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName) {
                val values: Map<out KSDeclaration, *> = readAnnotationRawValues(annotationMirror)
                val converted: MutableMap<CharSequence, Any> = mutableMapOf()
                for ((key, value1) in values) {
                    val value = value1!!
                    readAnnotationRawValues(
                        originatingElement,
                        annotationName,
                        member,
                        key.simpleName.toString(),
                        value,
                        converted
                    )
                }
                return OptionalValues.of(Any::class.java, converted)
            }
        }
        return OptionalValues.empty<Any>()
    }

    override fun getAnnotationMemberName(member: KSDeclaration): String {
        return member.simpleName.asString()
    }

    override fun getRepeatableName(annotationMirror: KSAnnotation?): String? {
        TODO("Not yet implemented")
    }

    override fun getRepeatableNameForType(annotationType: KSDeclaration): String? {
        val name = java.lang.annotation.Repeatable::class.java.name
        val repeatable = annotationType.annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == name
        }
        if (repeatable != null) {
            val value = repeatable.arguments.find { it.name?.asString() == "value" }?.value
            if (value != null) {
                return (value as Class<*>).name
            }
        }
        return null
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
        val retention = annotation.annotations.find {
            getAnnotationTypeName(it) == java.lang.annotation.Retention::class.java.name
        }
        if (retention != null) {
            val value = retention.arguments.find { it.name?.asString() == "value" }?.value
            if (value != null) {
                return value as RetentionPolicy
            }
        }
        return RetentionPolicy.RUNTIME
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
