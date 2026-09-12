/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.AnnotationElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy
import java.util.Collections
import java.util.EnumSet
import java.util.Optional

/**
 * Kotlin implementation of [io.micronaut.inject.ast.AnnotationElement].
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
internal class KotlinAnnotationElement(
    private val annotationNativeType: KotlinClassNativeElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : KotlinClassElement(
    annotationNativeType,
    elementAnnotationMetadataFactory,
    null,
    visitorContext
), AnnotationElement {

    // an annotation type cannot be generic, so the type arguments can be ignored
    override fun copyThis() = KotlinAnnotationElement(
        annotationNativeType,
        elementAnnotationMetadataFactory,
        visitorContext
    )

    override fun isInherited() = declaration.annotations.any { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == AnnotationUtil.ANN_INHERITED
    }

    /*
     * KSP presents a Java @Target on a compiled annotation as kotlin.annotation.Target with the Java element
     * types mapped to AnnotationTarget, so PACKAGE, MODULE and RECORD_COMPONENT, which have no Kotlin
     * target, cannot be reported for a Java annotation on the classpath.
     */
    override fun getTargets(): Set<ElementType> {
        val target = declaration.annotations.find { annotation ->
            val name = visitorContext.getAnnotationTypeName(annotation)
            name == KOTLIN_TARGET || name == JAVA_TARGET
        } ?: return AnnotationElement.DEFAULT_TARGETS
        val targets = EnumSet.noneOf(ElementType::class.java)
        for (argument in target.arguments) {
            addTargets(argument.value, targets)
        }
        return Collections.unmodifiableSet(targets)
    }

    override fun getRepeatableContainer(): Optional<String> {
        val container = visitorContext.getRepeatableContainerNameForType(declaration)
        if (container != null) {
            return Optional.of(container)
        }
        val kotlinRepeatable = declaration.annotations.any { annotation ->
            visitorContext.getAnnotationTypeName(annotation) == KOTLIN_REPEATABLE
        }
        if (kotlinRepeatable) {
            // Since Kotlin 1.6 the compiler generates the container of a kotlin.annotation.Repeatable
            // annotation as a nested class named Container
            return Optional.of(visitorContext.getBinaryName(declaration) + "\$Container")
        }
        return Optional.empty()
    }

    override fun getRetentionPolicy(): RetentionPolicy =
        visitorContext.annotationMetadataBuilder.getRetentionPolicy(declaration)

    private fun addTargets(value: Any?, targets: EnumSet<ElementType>) {
        when (value) {
            is List<*> -> value.forEach { addTargets(it, targets) }
            is Array<*> -> value.forEach { addTargets(it, targets) }
            is KSType -> addTargets(value.declaration, targets)
            is KSDeclaration -> {
                val elementType = when (value.simpleName.asString()) {
                    // kotlin.annotation.AnnotationTarget
                    "CLASS" -> ElementType.TYPE
                    "ANNOTATION_CLASS" -> ElementType.ANNOTATION_TYPE
                    "VALUE_PARAMETER" -> ElementType.PARAMETER
                    "FUNCTION", "PROPERTY_GETTER", "PROPERTY_SETTER" -> ElementType.METHOD
                    "TYPE" -> if (isKotlinTarget(value)) ElementType.TYPE_USE else ElementType.TYPE
                    "TYPE_PARAMETER" -> ElementType.TYPE_PARAMETER
                    "FIELD" -> ElementType.FIELD
                    "CONSTRUCTOR" -> ElementType.CONSTRUCTOR
                    "LOCAL_VARIABLE" -> ElementType.LOCAL_VARIABLE
                    // PROPERTY, EXPRESSION, FILE and TYPEALIAS have no java.lang.annotation.ElementType
                    "PROPERTY", "EXPRESSION", "FILE", "TYPEALIAS" -> null
                    // java.lang.annotation.ElementType
                    else -> ElementType.entries.find { it.name == value.simpleName.asString() }
                }
                if (elementType != null) {
                    targets.add(elementType)
                }
            }
            else -> {}
        }
    }

    private fun isKotlinTarget(constant: KSDeclaration) =
        constant.parentDeclaration?.qualifiedName?.asString() == KOTLIN_ANNOTATION_TARGET

    companion object {
        private const val KOTLIN_TARGET = "kotlin.annotation.Target"
        private const val KOTLIN_REPEATABLE = "kotlin.annotation.Repeatable"
        private const val JAVA_TARGET = "java.lang.annotation.Target"
        private const val KOTLIN_ANNOTATION_TARGET = "kotlin.annotation.AnnotationTarget"
    }
}
