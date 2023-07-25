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
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isDefault
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.util.ArrayUtils
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.getClassDeclaration
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.Method
import java.util.*

internal class KotlinAnnotationMetadataBuilder(private val symbolProcessorEnvironment: SymbolProcessorEnvironment,
                                      private val resolver: Resolver,
                                      private val visitorContext: KotlinVisitorContext): AbstractAnnotationMetadataBuilder<KSAnnotated, KSAnnotation>() {

    private val annotationDefaultsCache: ConcurrentLinkedHashMap<String, MutableMap<out KSDeclaration, *>> =
        ConcurrentLinkedHashMap.Builder<String, MutableMap<out KSDeclaration, *>>().maximumWeightedCapacity(200).build()

    companion object {
        private fun getTypeForAnnotation(annotationMirror: KSAnnotation, visitorContext: KotlinVisitorContext): KSClassDeclaration {
            return annotationMirror.annotationType.resolve().declaration.getClassDeclaration(visitorContext)
        }
        fun getAnnotationTypeName(resolver: Resolver, annotationMirror: KSAnnotation, visitorContext: KotlinVisitorContext): String {
            val type = getTypeForAnnotation(annotationMirror, visitorContext)
            return type.getBinaryName(resolver, visitorContext)
        }
    }

    override fun getTypeForAnnotation(annotationMirror: KSAnnotation): KSClassDeclaration {
        return Companion.getTypeForAnnotation(annotationMirror, visitorContext)
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
        return if (element is KSPropertyDeclaration) {
            element.annotations.iterator().hasNext() ||
                    element.getter?.annotations?.iterator()?.hasNext() ?: false
        } else {
            element.annotations.iterator().hasNext()
        }
    }

    override fun getAnnotationTypeName(annotationMirror: KSAnnotation): String {
        return Companion.getAnnotationTypeName(resolver, annotationMirror, visitorContext)
    }

    override fun getElementName(element: KSAnnotated): String {
        if (element is KSDeclaration) {
            return if (element is KSClassDeclaration) {
                element.qualifiedName!!.asString()
            } else {
                element.simpleName.asString()
            }
        }
        TODO("Not yet implemented")
    }

    override fun getAnnotationsForType(element: KSAnnotated): MutableList<out KSAnnotation> {
        val annotationMirrors : MutableList<KSAnnotation> = mutableListOf()

        when (element) {
            is KSValueParameter -> {
                // fuse annotations for setter and property
                val parent = element.parent
                if (parent is KSPropertySetter) {
                    val property = parent.parent
                    if (property is KSPropertyDeclaration) {
                        annotationMirrors.addAll(property.annotations)
                    }
                    annotationMirrors.addAll(parent.annotations)
                }
                annotationMirrors.addAll(element.annotations)
            }

            is KSPropertyGetter, is KSPropertySetter -> {
                val property = element.parent
                if (property is KSPropertyDeclaration) {
                    annotationMirrors.addAll(property.annotations)
                }
                annotationMirrors.addAll(element.annotations)
            }

            is KSPropertyDeclaration -> {
                val parent : KSClassDeclaration? = findClassDeclaration(element)
                if (parent is KSClassDeclaration) {
                    if (parent.classKind == ClassKind.ANNOTATION_CLASS) {
                        annotationMirrors.addAll(element.annotations)
                        val getter = element.getter
                        if (getter != null) {
                            annotationMirrors.addAll(getter.annotations)
                        }
                    }
                }
                annotationMirrors.addAll(element.annotations)
            }

            else -> {
                annotationMirrors.addAll(element.annotations)
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
        if (element is KSValueParameter) {
            handleNullability(element.type.resolve(), annotationMetadata)
        } else if (element is KSFunctionDeclaration) {
            val ksType = element.returnType?.resolve()
            if (ksType != null) {
                handleNullability(ksType, annotationMetadata)
            }
        } else if (element is KSPropertyDeclaration) {
            handleNullability(element.type.resolve(), annotationMetadata)
        } else if (element is KSPropertySetter) {
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
        if (declaredOnly) {
            return mutableListOf(element)
        }
        when (element) {

            is KSValueParameter -> {
                val parent = element.parent
                return if (parent is KSFunctionDeclaration) {
                    if (parent.isConstructor()) {
                        mutableListOf(element)
                    } else {
                        val parameters = parent.parameters
                        val parameterIndex =
                            parameters.indexOf(parameters.find { it.name!!.asString() == element.name!!.asString() })
                        methodsHierarchy(parent)
                            .map { if (it == parent) element else it.parameters[parameterIndex] }
                            .toMutableList()
                    }
                } else { // Setter
                    mutableListOf(element)
                }
            }

            is KSClassDeclaration -> {
                val hierarchy = mutableListOf<KSAnnotated>()
                hierarchy.add(element)
                if (element.classKind == ClassKind.ANNOTATION_CLASS) {
                    return hierarchy
                }
                populateTypeHierarchy(element, hierarchy)
                hierarchy.reverse()
                return hierarchy
            }

            is KSFunctionDeclaration -> {
                val methodsHierarchy = methodsHierarchy(element)
                val hierarchy = mutableListOf<KSAnnotated>()
                hierarchy.addAll(methodsHierarchy)
                return hierarchy
            }

            else -> {
                return mutableListOf(element)
            }
        }
    }

    private fun methodsHierarchy(element: KSFunctionDeclaration): List<KSFunctionDeclaration> =
        if (element.isConstructor()) {
            listOf(element)
        } else {
            val hierarchy = mutableListOf(element)
            var overriden = element.findOverridee() as KSFunctionDeclaration?
            while (overriden != null) {
                hierarchy.add(overriden)
                overriden = overriden.findOverridee() as KSFunctionDeclaration?
            }
            hierarchy.reverse()
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
        return when (annotationValue) {
            is Collection<*> -> {
                toArray(annotationValue, originatingElement)
            }
            is Array<*> -> {
                toArray(annotationValue.toList(), originatingElement)
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
        originatingElement: KSAnnotated
    ): Array<out Any>? {
        var valueType = Any::class.java
        val collection = annotationValue.mapNotNull {
            val v = readAnnotationValue(originatingElement, it)
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
        // issue getting default values for an annotation here
        // TODO: awful hack due to https://github.com/google/ksp/issues/642 and not being able to access annotation defaults for a type
        val classDeclaration = annotationType.getClassDeclaration(visitorContext)
        val qualifiedName = classDeclaration.qualifiedName
        return if (qualifiedName != null) {
            annotationDefaultsCache.computeIfAbsent(qualifiedName.asString()) {
                readDefaultValuesReflectively(
                    classDeclaration,
                    annotationType,
                    "getDescriptor",
                    "getJClass",
                    "getMethods"
                )
            }
        } else {
            mutableMapOf<KSDeclaration, Any>()
        }
    }

    override fun getOriginatingClassName(orginatingElement: KSAnnotated): String? {
        val binaryName = if (orginatingElement is KSClassDeclaration) {
            orginatingElement.getBinaryName(resolver, visitorContext)
        } else {
            val classDeclaration = orginatingElement.getClassDeclaration(visitorContext)
            classDeclaration.getBinaryName(resolver, visitorContext)
        }
        return if (binaryName != Object::javaClass.name) {
            binaryName
        } else {
            null
        }
    }

    private fun readDefaultValuesReflectively(classDeclaration : KSClassDeclaration, annotationType: KSAnnotated, vararg path : String): MutableMap<KSDeclaration, Any> {
        var o: Any? = findValueReflectively(annotationType, *path)
        val declaredProperties = classDeclaration.getDeclaredProperties()
        val map = mutableMapOf<KSDeclaration, Any>()
        if (o != null) {
            if (o is Iterable<*>) {
                for (m in o) {
                    if (m != null) {
                        val name = findValueReflectively(m, "getName")
                        // currently only handles JavaLiteralAnnotationArgument but probably should handle others
                        val value =
                            findValueReflectively(m, "getAnnotationParameterDefaultValue", "getValue")
                        if (value != null && name != null) {
                            val ksPropertyDeclaration = declaredProperties.find { it.simpleName.asString() == name.toString() }
                            if (ksPropertyDeclaration != null) {
                                map[ksPropertyDeclaration] = value
                            }
                        }
                    }
                }
            }
        }
        return map
    }

    private fun findValueReflectively(
        root: Any,
        vararg path : String
    ): Any? {
        var m: Method?
        var o: Any = root
        for (p in path) {
            m = ReflectionUtils.findMethod(o.javaClass, p).orElse(null)
            if (m == null) {
                return null
            } else {
                try {
                    o = m.invoke(o)
                    if (o == null) {
                        return null
                    }
                } catch (e: Exception) {
                    return null
                }
            }
        }
        return o
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
        return getRepeatableNameForType(annotationMirror.annotationType)
    }

    override fun getRepeatableNameForType(annotationType: KSAnnotated): String? {
        val name = java.lang.annotation.Repeatable::class.java.name
        val repeatable = annotationType.getClassDeclaration(visitorContext).annotations.find {
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

    override fun getAnnotationMirror(annotationName: String): Optional<KSAnnotated> {
        return Optional.ofNullable(resolver.getClassDeclarationByName(annotationName))
    }

    override fun getAnnotationMember(annotationElement: KSAnnotated, member: CharSequence): KSAnnotated? {
        if (annotationElement is KSClassDeclaration) {
            return annotationElement.getAllProperties().find { it.simpleName.asString() == member }
        }
        throw IllegalStateException("Unknown annotation element: $annotationElement")
    }

    override fun getAnnotationMemberName(member: KSAnnotated): String {
        if (member is KSPropertyDeclaration) {
            return member.simpleName.asString()
        }
        throw IllegalStateException("Unknown annotation member element: $member")
    }

    override fun createVisitorContext(): VisitorContext {
        return KotlinVisitorContext(symbolProcessorEnvironment, resolver)
    }

    override fun getRetentionPolicy(annotation: KSAnnotated): RetentionPolicy {
        var retention = annotation.annotations.find {
            getAnnotationTypeName(it) == java.lang.annotation.Retention::class.java.name
        }
        if (retention != null) {
            val value = retention.arguments.find { it.name?.asString() == "value" }?.value
            if (value is KSType) {
                return toRetentionPolicy(value)
            }
        } else {
            retention = annotation.annotations.find {
                getAnnotationTypeName(it) == Retention::class.java.name
            }
            if (retention != null) {
                val value = retention.arguments.find { it.name?.asString() == "value" }?.value
                if (value is KSType) {
                    return toJavaRetentionPolicy(value)
                }
            }
        }
        return RetentionPolicy.RUNTIME
    }

    private fun toRetentionPolicy(value: KSType) =
        RetentionPolicy.valueOf(value.declaration.qualifiedName!!.getShortName())

    private fun toJavaRetentionPolicy(value: KSType) =
        when (AnnotationRetention.valueOf(value.declaration.qualifiedName!!.getShortName())) {
            AnnotationRetention.RUNTIME -> {
                RetentionPolicy.RUNTIME
            }

            AnnotationRetention.SOURCE -> {
                RetentionPolicy.SOURCE
            }

            AnnotationRetention.BINARY -> {
                RetentionPolicy.CLASS
            }
    }

    private fun populateTypeHierarchy(element: KSClassDeclaration, hierarchy: MutableList<KSAnnotated>) {
        element.superTypes.forEach {
            val t = it.resolve()
            if (t != resolver.builtIns.anyType) {
                val declaration = t.declaration
                if (!hierarchy.contains(declaration)) {
                    hierarchy.add(declaration)
                    populateTypeHierarchy(declaration.getClassDeclaration(visitorContext), hierarchy)
                }
            }
        }
    }

    private fun readAnnotationValue(originatingElement: KSAnnotated, value: Any?): Any? {
        if (value == null) {
            return null
        }
        if (value is KSType) {
            val declaration = value.declaration
            if (declaration is KSClassDeclaration) {
                if (declaration.classKind == ClassKind.ENUM_ENTRY) {
                    return declaration.qualifiedName?.getShortName()
                }
                if (declaration.classKind == ClassKind.CLASS ||
                    declaration.classKind == ClassKind.INTERFACE ||
                    declaration.classKind == ClassKind.ANNOTATION_CLASS) {
                    return AnnotationClassValue<Any>(declaration.getBinaryName(resolver, visitorContext))
                }
            }
        }
        if (value is KSAnnotation) {
            return readNestedAnnotationValue(originatingElement, value)
        }

         return value
    }

}
