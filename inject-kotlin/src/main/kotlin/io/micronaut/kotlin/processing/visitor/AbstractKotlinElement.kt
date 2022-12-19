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
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isJavaPackagePrivate
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.ElementMutableAnnotationMetadataDelegate
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.unwrap
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

abstract class AbstractKotlinElement<T : KSNode>(val declaration: T,
                                     protected  val annotationMetadataFactory: ElementAnnotationMetadataFactory,
                                     protected val visitorContext: KotlinVisitorContext) : Element, ElementMutableAnnotationMetadataDelegate<Element> {

    protected var presetAnnotationMetadata: AnnotationMetadata? = null
    private var elementAnnotationMetadata: ElementAnnotationMetadata? = null

    override fun getNativeType(): T {
        return declaration
    }

    override fun isProtected(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.getVisibility() == Visibility.PROTECTED
        } else {
            false
        }
    }

    override fun isStatic(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.modifiers.contains(Modifier.JAVA_STATIC)
        } else {
            false
        }
    }

    protected fun makeCopy(): AbstractKotlinElement<T> {
        val element: AbstractKotlinElement<T> = copyThis()
        copyValues(element)
        return element
    }

    /**
     * @return copy of this element
     */
    protected abstract fun copyThis(): AbstractKotlinElement<T>

    /**
     * @param element the values to be copied to
     */
    protected open fun copyValues(element: AbstractKotlinElement<T>) {
        element.presetAnnotationMetadata = presetAnnotationMetadata
    }
    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): Element? {
        val kotlinElement: AbstractKotlinElement<T> = makeCopy()
        kotlinElement.presetAnnotationMetadata = annotationMetadata
        return kotlinElement
    }

    override fun getAnnotationMetadata(): MutableAnnotationMetadataDelegate<*> {
        if (elementAnnotationMetadata == null) {

            val factory = annotationMetadataFactory
            if (presetAnnotationMetadata == null) {
                elementAnnotationMetadata = factory.build(this)
            } else {
                elementAnnotationMetadata = factory.build(this, presetAnnotationMetadata)
            }
        }
        return elementAnnotationMetadata!!
    }

    override fun isPublic(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.getVisibility() == Visibility.PUBLIC
        } else {
            false
        }
    }

    override fun isPrivate(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.getVisibility() == Visibility.PRIVATE
        } else {
            false
        }
    }

    override fun isFinal(): Boolean {
        return if (declaration is KSDeclaration) {
            !declaration.isOpen() || declaration.modifiers.contains(Modifier.FINAL)
        } else {
            false
        }
    }

    override fun isAbstract(): Boolean {
        return if (declaration is KSModifierListOwner) {
            declaration.modifiers.contains(Modifier.ABSTRACT)
        } else {
            false
        }
    }

    @OptIn(KspExperimental::class)
    override fun getModifiers(): MutableSet<ElementModifier> {
        val dec = declaration.unwrap()
        if (dec is KSDeclaration) {
            val javaModifiers = visitorContext.resolver.effectiveJavaModifiers(dec)
            return javaModifiers.mapNotNull {
                when (it) {
                    Modifier.FINAL -> ElementModifier.FINAL
                    Modifier.PRIVATE, Modifier.INTERNAL -> ElementModifier.PRIVATE
                    Modifier.PROTECTED -> ElementModifier.PROTECTED
                    Modifier.ABSTRACT -> ElementModifier.ABSTRACT
                    Modifier.JAVA_STATIC -> ElementModifier.STATIC
                    Modifier.PUBLIC -> ElementModifier.PUBLIC
                    Modifier.JAVA_TRANSIENT -> ElementModifier.TRANSIENT
                    else -> null
                }
            }.toMutableSet()
        }
        return super.getModifiers()
    }

    override fun <T : Annotation?> annotate(
        annotationType: String?,
        consumer: Consumer<AnnotationValueBuilder<T>>?
    ): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType, consumer)
    }

    override fun annotate(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType)
    }

    override fun <T : Annotation?> annotate(
        annotationType: Class<T>?,
        consumer: Consumer<AnnotationValueBuilder<T>>?
    ): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType, consumer)
    }

    override fun <T : Annotation?> annotate(annotationType: Class<T>?): Element? {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType)
    }
    override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationValue)
    }

    override fun removeAnnotation(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotation(annotationType: Class<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotationIf(predicate)
    }

    override fun removeStereotype(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeStereotype(annotationType)
    }

    override fun <T : Annotation?> removeStereotype(annotationType: Class<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeStereotype(annotationType)
    }

    override fun isPackagePrivate(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.isJavaPackagePrivate()
        } else {
            false
        }
    }

    override fun getDocumentation(): Optional<String> {
        return if (declaration is KSDeclaration) {
            Optional.ofNullable(declaration.docString)
        } else {
            Optional.empty()
        }
    }

    override fun getReturnInstance(): Element {
        return this
    }

    protected fun resolveGeneric(
        parent: KSNode?,
        type: ClassElement,
        owningClass: ClassElement,
        visitorContext: KotlinVisitorContext
    ): ClassElement {
        var resolvedType = type
        if (parent is KSDeclaration && owningClass is KotlinClassElement) {
            if (type is GenericPlaceholderElement) {

                val variableName = type.variableName
                val genericTypeInfo = owningClass.getGenericTypeInfo()
                val boundInfo = genericTypeInfo[parent.getBinaryName(visitorContext.resolver)]
                if (boundInfo != null) {
                    val ksType = boundInfo[variableName]
                    if (ksType != null) {
                        resolvedType = visitorContext.elementFactory.newClassElement(
                            ksType,
                            visitorContext.elementAnnotationMetadataFactory,
                            false
                        )
                        if (type.isArray) {
                            resolvedType = resolvedType.toArray()
                        }
                    }
                }
            } else if (type.declaredGenericPlaceholders.isNotEmpty()) {
                resolvedType = type.foldBoundGenericTypes {
                    if (it is GenericPlaceholderElement) {
                        resolveGeneric(parent, it, owningClass, visitorContext)
                    } else {
                        it
                    }
                }
            }
        }
        return resolvedType
    }

    override fun toString(): String {
        return getDescription(false)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractKotlinElement<*>

        if (nativeType != other.nativeType) return false

        return true
    }

    override fun hashCode(): Int {
        return nativeType.hashCode()
    }


}
