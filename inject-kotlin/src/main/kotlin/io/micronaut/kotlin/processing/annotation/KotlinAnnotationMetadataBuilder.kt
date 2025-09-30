/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.annotation

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Origin
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.util.ArrayUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.getClassDeclaration
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.lang.annotation.Repeatable
import java.lang.annotation.RetentionPolicy
import java.util.*

internal class KotlinAnnotationMetadataBuilder(
    private val symbolProcessorEnvironment: SymbolProcessorEnvironment,
    var resolver: Resolver,
    private val visitorContext: KotlinVisitorContext
) : AbstractAnnotationMetadataBuilder<KSAnnotated, KSAnnotation>() {

    companion object {
        private fun getTypeForAnnotation(annotationMirror: KSAnnotation, visitorContext: KotlinVisitorContext): KSClassDeclaration {
            return annotationMirror.annotationType.resolve().declaration.getClassDeclaration(visitorContext)
        }
        fun getAnnotationTypeName(resolver: Resolver, annotationMirror: KSAnnotation, visitorContext: KotlinVisitorContext): String {
            val type = getTypeForAnnotation(annotationMirror, visitorContext)
            return type.getBinaryName(resolver, visitorContext)
        }
    }

    override fun getTypeForAnnotation(annotationMirror: KSAnnotation): KSAnnotated {
        return KotlinAnnotationType(annotationMirror, Companion.getTypeForAnnotation(annotationMirror, visitorContext))
    }

    override fun hasAnnotation(element: KSAnnotated, annotation: Class<out Annotation>): Boolean {
        return hasAnnotation(element, annotation.name)
    }

    override fun hasAnnotation(element: KSAnnotated, annotation: String): Boolean {
        return element.annotations.map {
            it.annotationType.resolve().declaration.qualifiedName
        }.any {
            it?.asString() == annotation
        }
    }

    override fun hasAnnotations(element: KSAnnotated): Boolean {
        var annotated = element
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        return if (annotated is KSPropertyDeclaration) {
            annotated.annotations.iterator().hasNext() ||
                    annotated.getter?.annotations?.iterator()?.hasNext() ?: false
        } else {
            annotated.annotations.iterator().hasNext()
        }
    }

    override fun getAnnotationTypeName(annotationMirror: KSAnnotation): String {
        return Companion.getAnnotationTypeName(resolver, annotationMirror, visitorContext)
    }

    override fun getElementName(element: KSAnnotated): String {
        var annotated = element
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        if (annotated is KSDeclaration) {
            return if (annotated is KSClassDeclaration) {
                annotated.qualifiedName!!.asString()
            } else {
                annotated.simpleName.asString()
            }
        }
        TODO("Not yet implemented")
    }

    override fun getAnnotationsForType(element: KSAnnotated): MutableList<out KSAnnotation> {
        val annotationMirrors : MutableList<KSAnnotation> = mutableListOf()

        var annotated = element
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        when (annotated) {
            is KSValueParameter -> {
                // fuse annotations for setter and property
                val parent = annotated.parent
                if (parent is KSPropertySetter) {
                    val property = parent.parent
                    if (property is KSPropertyDeclaration) {
                        annotationMirrors.addAll(property.annotations)
                    }
                    annotationMirrors.addAll(parent.annotations)
                }
                annotationMirrors.addAll(annotated.annotations)
            }

            is KSPropertyGetter, is KSPropertySetter -> {
                val property = annotated.parent
                if (property is KSPropertyDeclaration) {
                    annotationMirrors.addAll(property.annotations)
                }
                annotationMirrors.addAll(annotated.annotations)
            }

            is KSPropertyDeclaration -> {
                val parent : KSClassDeclaration? = findClassDeclaration(annotated)
                if (parent is KSClassDeclaration) {
                    if (parent.classKind == ClassKind.ANNOTATION_CLASS) {
                        annotationMirrors.addAll(annotated.annotations)
                        val getter = annotated.getter
                        if (getter != null) {
                            annotationMirrors.addAll(getter.annotations)
                        }
                    }
                }
                annotationMirrors.addAll(annotated.annotations)
            }

            else -> {
                annotationMirrors.addAll(annotated.annotations)
            }
        }
        val expanded : MutableList<KSAnnotation> = mutableListOf()
        for (ann in annotationMirrors) {
            val annotationName = getAnnotationTypeName(ann)
            var repeateable = false
            var hasOtherMembers = false
            for (arg in ann.arguments) {
                if ("value" == arg.name?.asString()) {
                    val value = arg.value
                    if (value is Iterable<*>) {
                        for (nested in value) {
                            if (nested is KSAnnotation) {
                                val repeatableName = getRepeatableName(nested)
                                if (repeatableName != null && repeatableName == annotationName) {
                                    expanded.add(nested)
                                    repeateable = true
                                }
                            }
                        }
                    }
                } else {
                    hasOtherMembers = true
                }
            }

            if (!repeateable || hasOtherMembers) {
                expanded.add(ann)
            }
        }
        return expanded
    }

    private fun findClassDeclaration(element: KSPropertyDeclaration): KSClassDeclaration? {
        var parent = element.parent
        while (parent != null) {
           if (parent is KSClassDeclaration) {
               return parent
           }
           parent = parent.parent
        }
        return null
    }

    override fun postProcess(annotationMetadata: MutableAnnotationMetadata, element: KSAnnotated) {
        var annotated = element
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        if (annotated is KSValueParameter) {
            handleNullability(annotated.type.resolve(), annotationMetadata)
        } else if (annotated is KSFunctionDeclaration) {
            val ksType = annotated.returnType?.resolve()
            if (ksType != null) {
                handleNullability(ksType, annotationMetadata)
            }
        } else if (annotated is KSPropertyDeclaration) {
            handleNullability(annotated.type.resolve(), annotationMetadata)
        } else if (annotated is KSPropertySetter) {
            if (!annotationMetadata.hasAnnotation(JvmField::class.java) && (annotationMetadata.hasStereotype(AnnotationUtil.QUALIFIER) || annotationMetadata.hasAnnotation(Property::class.java))) {
                // implicitly inject
                annotationMetadata.addDeclaredAnnotation(AnnotationUtil.INJECT, emptyMap())
            }
        }
    }

    private fun handleNullability(
        ksType: KSType,
        annotationMetadata: MutableAnnotationMetadata
    ) {
        if (ksType.isMarkedNullable) {
            // explicitly allowed to be null so add nullable
            annotationMetadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, emptyMap())
        } else {
            // with Kotlin the default is not null, so we must store in the metadata
            // that the element is not nullable
            annotationMetadata.addDeclaredAnnotation(AnnotationUtil.NON_NULL, emptyMap())
        }
    }

    override fun buildHierarchy(
        element: KSAnnotated,
        inheritTypeAnnotations: Boolean,
        declaredOnly: Boolean
    ): MutableList<KSAnnotated> {
        var annotated = element
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        if (declaredOnly) {
            return mutableListOf(annotated)
        }
        when (annotated) {

            is KSValueParameter -> {
                val parent = annotated.parent
                return if (parent is KSFunctionDeclaration) {
                    if (parent.isConstructor()) {
                        mutableListOf(annotated)
                    } else {
                        val parameters = parent.parameters
                        val parameterIndex =
                            parameters.indexOf(parameters.find { it.name!!.asString() == annotated.name!!.asString() })
                        methodsHierarchy(parent)
                            .map { if (it == parent) annotated else it.parameters[parameterIndex] }
                            .toMutableList()
                    }
                } else { // Setter
                    mutableListOf(annotated)
                }
            }

            is KSClassDeclaration -> {
                val hierarchy = mutableListOf<KSClassDeclaration>()
                if (annotated.classKind == ClassKind.ANNOTATION_CLASS) {
                    hierarchy.add(annotated)
                } else {
                    visitorContext.nativeElementsHelper.populateTypeHierarchy(annotated, hierarchy)
                }
                return hierarchy as MutableList<KSAnnotated>
            }

            is KSFunctionDeclaration -> {
                val hierarchy = mutableListOf<KSAnnotated>()
                hierarchy.addAll(methodsHierarchy(annotated))
                return hierarchy
            }

            else -> {
                return mutableListOf(annotated)
            }
        }
    }

    private fun methodsHierarchy(element: KSFunctionDeclaration): List<KSFunctionDeclaration> =
        if (element.isConstructor()) {
            listOf(element)
        } else {
            val hierarchy = mutableListOf<KSFunctionDeclaration>()
            hierarchy.addAll(visitorContext.nativeElementsHelper.findOverriddenMethods(element))
            hierarchy.add(element)
            hierarchy
        }

    override fun readAnnotationRawValues(
        originatingElement: KSAnnotated,
        annotationName: String,
        member: KSAnnotated,
        memberName: String,
        annotationValue: Any,
        annotationValues: MutableMap<CharSequence, Any>
    ) {
        if (!annotationValues.containsKey(memberName)) {
            val value = readAnnotationValue(originatingElement, member, annotationName, memberName, annotationValue)
            if (value != null) {
                validateAnnotationValue(originatingElement, annotationName, member, memberName, value)
                annotationValues[memberName] = value
            }
        }
    }

    override fun isValidationRequired(member: KSAnnotated?): Boolean {
        if (member != null) {
            return member.annotations.any {
                val name = it.annotationType.resolve().declaration.qualifiedName?.asString()
                if (name != null) {
                    return name.startsWith("jakarta.validation")
                } else {
                    return false
                }
            }
        }
        return false
    }

    override fun addError(originatingElement: KSAnnotated, error: String) {
        symbolProcessorEnvironment.logger.error(error, originatingElement)
    }

    override fun addWarning(originatingElement: KSAnnotated, warning: String) {
        symbolProcessorEnvironment.logger.warn(warning, originatingElement)
    }

    override fun readAnnotationValue(
        originatingElement: KSAnnotated,
        member: KSAnnotated,
        annotationName: String,
        memberName: String,
        annotationValue: Any
    ): Any? {
        val property = member as KSPropertyDeclaration
        return when (annotationValue) {
            is Collection<*> -> {
                toArray(annotationValue, member, property.type)
            }
            is Array<*> -> {
                toArray(annotationValue.toList(), member, property.type)
            }
            else -> {
                if (isEvaluatedExpression(annotationValue)) {
                    return buildEvaluatedExpressionReference(
                        originatingElement,
                        annotationName,
                        memberName,
                        annotationValue
                    )
                } else {
                    return readAnnotationValue(originatingElement, annotationValue)
                }
            }
        }
    }

    private fun toArray(
        annotationValue: Collection<*>,
        element: KSAnnotated,
        type: KSTypeReference
    ): Array<out Any>? {
        var valueType = Any::class.java
        if (annotationValue.isEmpty()) {
            val arrayType = type.resolve()
            if (arrayType.declaration.qualifiedName?.asString() == "kotlin.Array") {
                val className = arrayType.arguments[0].type!!.resolve().declaration.getBinaryName(resolver, visitorContext);
                val optionalClassName = findJavaClass(className)
                if (optionalClassName.isPresent) {
                    valueType = optionalClassName.get() as Class<Any>
                }
            }
        }
        val collection = annotationValue.mapNotNull {
            val v = readAnnotationValue(element, it)
            if (v != null) {
                valueType = v.javaClass
            }
            v
        } // annotation values can't be null
        return ArrayUtils.toArray(collection, valueType)
    }

    override fun readAnnotationDefaultValues(
        annotationName: String,
        annotationType: KSAnnotated
    ): MutableMap<out KSDeclaration, *> {
        return if (annotationType is KotlinAnnotationType) {
            val map = mutableMapOf<KSDeclaration, Any>()
            annotationType.type.getAllProperties().forEach { prop ->
                val argument = annotationType.mirror.defaultArguments.find { it.name == prop.simpleName }
                if (argument?.value != null && argument.isDefault()) {
                    val value = argument.value!!
                    if (value !is String || !StringUtils.isEmpty(value)) {
                        map[prop] = value
                    }
                }
            }
            map
        } else {
            mutableMapOf<KSDeclaration, Any>()
        }
    }

    override fun getOriginatingClassName(orginatingElement: KSAnnotated): String? {
        var annotated = orginatingElement
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        val binaryName = if (annotated is KSClassDeclaration) {
            annotated.getBinaryName(resolver, visitorContext)
        } else {
            val classDeclaration = annotated.getClassDeclaration(visitorContext)
            classDeclaration.getBinaryName(resolver, visitorContext)
        }
        return if (binaryName != Object::javaClass.name) {
            binaryName
        } else {
            null
        }
    }

    override fun readAnnotationRawValues(annotationMirror: KSAnnotation): MutableMap<out KSDeclaration, *> {
        val map = mutableMapOf<KSDeclaration, Any>()
        val declaration = annotationMirror.annotationType.resolve().declaration.getClassDeclaration(visitorContext)
        declaration.getAllProperties().forEach { prop ->
            val argument = annotationMirror.arguments.find { it.name == prop.simpleName }
            if (argument?.value != null && !argument.isDefault()) {
                val value = argument.value!!
                map[prop] = value
            }
        }
        return map
    }

    override fun <K : Annotation> getAnnotationValues(
        originatingElement: KSAnnotated,
        member: KSAnnotated?,
        annotationType: Class<K>
    ): Optional<AnnotationValue<K>> {
        val annotationMirrors: MutableList<KSAnnotation> = (member as KSPropertyDeclaration).getter!!.annotations.toMutableList()
        annotationMirrors.addAll(member.annotations.toList())
        val annotationName = annotationType.name
        for (annotationMirror in annotationMirrors) {
            if (annotationMirror.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName) {
                val values: Map<out KSDeclaration, *> = readAnnotationRawValues(annotationMirror)
                val converted: MutableMap<CharSequence, Any> = mutableMapOf()
                for ((key, value1) in values) {
                    var value = value1!!
                    val memberName = key.simpleName.asString()
                    if (isEvaluatedExpression(value)) {
                        value = buildEvaluatedExpressionReference(
                            originatingElement,
                            annotationName,
                            memberName,
                            value
                        )
                    }
                    readAnnotationRawValues(
                        originatingElement,
                        annotationName,
                        key,
                        memberName,
                        value,
                        converted
                    )
                }
                return Optional.of(
                    AnnotationValue.builder(annotationType).members(converted).build()
                )
            }
        }
        return Optional.empty()
    }

    override fun getRepeatableName(annotationMirror: KSAnnotation): String? {
        val repeatableContainer =
            getRepeatableContainerNameForType(annotationMirror.annotationType.getClassDeclaration(visitorContext))
        return repeatableContainer
    }

    override fun getRepeatableContainerNameForType(annotationType: KSAnnotated): String? {
        val name = Repeatable::class.java.name
        val repeatable = annotationType.annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == name
        }
        if (repeatable != null) {
            val value = repeatable.arguments.find { it.name?.asString() == "value" }?.value
            if (value != null) {
                val declaration = (value as KSType).declaration.getClassDeclaration(visitorContext)
                return declaration.getBinaryName(resolver, visitorContext)
            }
        }
        return null
    }

    override fun findRepeatableContainerNameForType(annotationName: String): String? {
        val container = super.findRepeatableContainerNameForType(annotationName)
        if (container == null) {
            return findJavaClass(annotationName)
            .flatMap<String> {
                for (annotation in it.annotations) {
                    if (annotation.annotationClass.java == Repeatable::class.java) {
                        return@flatMap Optional.of(
                            (annotation as Repeatable).value.java.name
                        )
                    }
                }
                return@flatMap Optional.empty()
            }
            .orElse(null)
        }
        return container
    }

    private fun findJavaClass(className: String): Optional<Class<*>> =
        ClassUtils.forName(className, null)
            .or { ClassUtils.forName(className, javaClass.classLoader) }
            .or { ClassUtils.forName(className, visitorContext::class.java.classLoader) }

    override fun getAnnotationMirror(annotationName: String): Optional<KSAnnotated> {
        return Optional.ofNullable(resolver.getClassDeclarationByName(annotationName))
    }

    override fun getAnnotationMember(annotationElement: KSAnnotated, member: CharSequence): KSAnnotated? {
        var annotated = annotationElement
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        if (annotated is KSClassDeclaration) {
            return annotated.getAllProperties().find { it.simpleName.asString() == member }
        }
        throw IllegalStateException("Unknown annotation element: $annotated")
    }

    override fun getAnnotationMemberName(member: KSAnnotated): String {
        var annotated = member
        if (annotated is KotlinAnnotationType) {
            annotated = annotated.type
        }
        if (annotated is KSPropertyDeclaration) {
            return annotated.simpleName.asString()
        }
        throw IllegalStateException("Unknown annotation member element: $annotated")
    }

    override fun getVisitorContext(): VisitorContext {
        return visitorContext
    }

    override fun getRetentionPolicy(annotation: KSAnnotated): RetentionPolicy {
        var retention = annotation.annotations.find {
            getAnnotationTypeName(it) == java.lang.annotation.Retention::class.java.name
        }
        var stringValue: String? = null
        if (retention != null) {
            val value = retention.arguments.find { it.name?.asString() == "value" }?.value
            val retentionValue = readAnnotationValue(annotation, value)
            if (retentionValue != null) {
                stringValue = retentionValue.toString().replace(java.lang.annotation.Retention::class.java.name + ".", "")
            }
        }
        if (stringValue == null) {
            retention = annotation.annotations.find {
                getAnnotationTypeName(it) == Retention::class.java.name
            }
            if (retention != null) {
                val value = retention.arguments.find { it.name?.asString() == "value" }?.value
                val retentionValue = readAnnotationValue(annotation, value)
                if (retentionValue != null) {
                    stringValue = retentionValue.toString().replace(Retention::class.java.name + ".", "")
                }
            }
        }
        return when(stringValue) {
            "SOURCE" -> RetentionPolicy.SOURCE
            "BINARY", "CLASS" -> RetentionPolicy.CLASS
            else -> RetentionPolicy.RUNTIME
        }
    }

    private fun readAnnotationValue(originatingElement: KSAnnotated, value: Any?): Any? {
        if (value == null) {
            return null
        }
        if (value is KSType) {
            return readKSType(value)
        }
        if (value is KSClassDeclaration) {
            return readKSClassDeclaration(value)
        }
        if (value is KSAnnotation) {
            return readNestedAnnotationValue(originatingElement, value)
        }
        return value
    }

    private fun readKSType(value: KSType): CharSequence? {
        val declaration = value.declaration
        if (declaration is KSClassDeclaration) {
            return readKSClassDeclaration(declaration)
        }
        if (declaration is KSTypeAlias) {
            return readKSType(declaration.type.resolve())
        }
        throw IllegalStateException("Unknown annotation element: $value $declaration " + declaration.javaClass)
    }

    private fun readKSClassDeclaration(declaration: KSClassDeclaration): CharSequence? {
        if (declaration.classKind == ClassKind.ENUM_ENTRY) {
            return declaration.qualifiedName?.getShortName()
        }
        if (declaration.classKind == ClassKind.CLASS ||
            declaration.classKind == ClassKind.OBJECT ||
            declaration.classKind == ClassKind.INTERFACE ||
            declaration.classKind == ClassKind.ENUM_CLASS ||
            declaration.classKind == ClassKind.ANNOTATION_CLASS
        ) {
            return AnnotationClassValue<Any>(declaration.getBinaryName(resolver, visitorContext))
        }
        throw IllegalStateException("Unknown KSClassDeclaration annotation element: $declaration ${declaration.classKind}")
    }

    private data class KotlinAnnotationType(
        var mirror: KSAnnotation,
        var type: KSClassDeclaration,
        override val annotations: Sequence<KSAnnotation> = type.annotations,
        override val location: Location = type.location,
        override val origin: Origin = type.origin,
        override val parent: KSNode? = type.parent
    ) : KSAnnotated {
        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return type.accept(visitor, data);
        }
    }

}
