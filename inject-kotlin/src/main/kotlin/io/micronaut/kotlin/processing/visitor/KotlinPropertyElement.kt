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

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataDelegate
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

class KotlinPropertyElement: AbstractKotlinElement<KSNode>, PropertyElement {

    private val name: String
    private val classElement: ClassElement
    private val type: ClassElement
    private val setter: Optional<MethodElement>
    private val getter: Optional<MethodElement>
    private val field: Optional<FieldElement>
    private val abstract: Boolean
    private var annotationMetadata: MutableAnnotationMetadataDelegate<*>? = null

    constructor(classElement: ClassElement,
                type: ClassElement,
                property: KSPropertyDeclaration,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext) : super(property, elementAnnotationMetadataFactory, visitorContext) {
        this.name = property.simpleName.asString()
        this.type = type
        this.classElement = classElement
        this.setter = Optional.ofNullable(property.setter)
            .map { method ->
                return@map if (method.modifiers.contains(Modifier.PRIVATE)) {
                    null
                } else {
                    visitorContext.elementFactory.newMethodElement(classElement, method, type, elementAnnotationMetadataFactory)
                }
            }
        this.getter = Optional.ofNullable(property.getter)
            .map { method ->
                return@map visitorContext.elementFactory.newMethodElement(classElement, method, type, elementAnnotationMetadataFactory)
            }
        this.abstract = property.isAbstract()
        if (property.hasBackingField) {
            this.field = Optional.of(visitorContext.elementFactory.newFieldElement(
                classElement,
                property,
                elementAnnotationMetadataFactory
            ))
        } else {
            this.field = Optional.empty()
        }

        val elements: MutableList<MemberElement> = ArrayList(3)
        setter.ifPresent { elements.add(it) }
        getter.ifPresent { elements.add(it) }
        field.ifPresent { elements.add(it) }


        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        val propertyAnnotationMetadata: AnnotationMetadata
        propertyAnnotationMetadata = if (elements.size == 1) {
            elements.iterator().next()
        } else {
            AnnotationMetadataHierarchy(
                true,
                *elements.stream().map { e: MemberElement ->
                    if (e is MethodElement) {
                        return@map object : AnnotationMetadataDelegate {
                            override fun getAnnotationMetadata(): AnnotationMetadata {
                                // Exclude type metadata
                                return e.getAnnotationMetadata().declaredMetadata
                            }
                        }
                    }
                    e
                }.toList().toTypedArray()
            )
        }
        this.annotationMetadata = object : MutableAnnotationMetadataDelegate<Any?> {
            override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationValue)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: String,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(annotationType: Class<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun annotate(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: Class<T>,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun removeAnnotation(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotation(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotationIf(predicate)
                }
                return this@KotlinPropertyElement
            }

            override fun getAnnotationMetadata(): AnnotationMetadata {
                return propertyAnnotationMetadata
            }
        }
    }
    constructor(classElement: ClassElement,
                type: ClassElement,
                name: String,
                getter: KSFunctionDeclaration,
                setter: KSFunctionDeclaration?,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext) : super(getter, elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.type = type
        this.classElement = classElement
        this.setter = Optional.ofNullable(setter)
            .map { method ->
                return@map visitorContext.elementFactory.newMethodElement(classElement, method, elementAnnotationMetadataFactory)
            }
        this.getter = Optional.of(visitorContext.elementFactory.newMethodElement(classElement, getter, elementAnnotationMetadataFactory))
        this.abstract = getter.isAbstract || setter?.isAbstract == true
        this.field = Optional.empty()
        val elements: MutableList<MemberElement> = ArrayList(3)
        this.setter.ifPresent { elements.add(it) }
        this.getter.ifPresent { elements.add(it) }
        field.ifPresent { elements.add(it) }

        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        val propertyAnnotationMetadata: AnnotationMetadata
        propertyAnnotationMetadata = if (elements.size == 1) {
            elements.iterator().next()
        } else {
            AnnotationMetadataHierarchy(
                true,
                *elements.stream().map { e: MemberElement ->
                    if (e is MethodElement) {
                        return@map object : AnnotationMetadataDelegate {
                            override fun getAnnotationMetadata(): AnnotationMetadata {
                                // Exclude type metadata
                                return e.getAnnotationMetadata().declaredMetadata
                            }
                        }
                    }
                    e
                }.toList().toTypedArray()
            )
        }
        this.annotationMetadata = object : MutableAnnotationMetadataDelegate<Any?> {
            override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationValue)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: String,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(annotationType: Class<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun annotate(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: Class<T>,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun removeAnnotation(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotation(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotationIf(predicate)
                }
                return this@KotlinPropertyElement
            }

            override fun getAnnotationMetadata(): AnnotationMetadata {
                return propertyAnnotationMetadata
            }
        }
    }

    constructor(classElement: ClassElement,
                type: ClassElement,
                name: String,
                field: FieldElement?,
                getter: MethodElement?,
                setter: MethodElement?,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext) : super(pickDeclaration(type, field, getter, setter), elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.type = type
        this.classElement = classElement
        this.setter = Optional.ofNullable(setter)
        this.getter = Optional.ofNullable(getter)
        this.abstract = getter?.isAbstract == true || setter?.isAbstract == true
        this.field = Optional.ofNullable(field)
        val elements: MutableList<MemberElement> = ArrayList(3)
        this.setter.ifPresent { elements.add(it) }
        this.getter.ifPresent { elements.add(it) }
        this.field.ifPresent { elements.add(it) }

        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        val propertyAnnotationMetadata: AnnotationMetadata
        propertyAnnotationMetadata = if (elements.size == 1) {
            elements.iterator().next()
        } else {
            AnnotationMetadataHierarchy(
                true,
                *elements.stream().map { e: MemberElement ->
                    if (e is MethodElement) {
                        return@map object : AnnotationMetadataDelegate {
                            override fun getAnnotationMetadata(): AnnotationMetadata {
                                // Exclude type metadata
                                return e.getAnnotationMetadata().declaredMetadata
                            }
                        }
                    }
                    e
                }.toList().toTypedArray()
            )
        }
        this.annotationMetadata = object : MutableAnnotationMetadataDelegate<Any?> {
            override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationValue)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: String,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(annotationType: Class<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun annotate(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: Class<T>,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun removeAnnotation(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotation(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotationIf(predicate)
                }
                return this@KotlinPropertyElement
            }

            override fun getAnnotationMetadata(): AnnotationMetadata {
                return propertyAnnotationMetadata
            }
        }
    }

    companion object Helper {
        private fun pickDeclaration(
            type: ClassElement,
            field: FieldElement?,
            getter: MethodElement?,
            setter: MethodElement?
        ): KSNode {
            return if (field?.nativeType != null) {
                field.nativeType as KSNode
            } else if (getter?.nativeType != null) {
                getter.nativeType as KSNode
            } else if (setter?.nativeType != null) {
                setter.nativeType as KSNode
            } else {
                type.nativeType as KSNode
            }
        }
    }

    override fun getAnnotationMetadata(): MutableAnnotationMetadataDelegate<*> {
        return this.annotationMetadata!!
    }

    override fun getField(): Optional<FieldElement> {
        return this.field
    }

    override fun getName(): String = name
    override fun getModifiers(): MutableSet<ElementModifier> {
        return super<AbstractKotlinElement>.getModifiers()
    }

    override fun getType(): ClassElement = type

    override fun getDeclaringType(): ClassElement = classElement

    override fun getReadMethod(): Optional<MethodElement> = getter

    override fun getWriteMethod(): Optional<MethodElement> = setter

    override fun isReadOnly(): Boolean {
        return !setter.isPresent || setter.get().isPrivate
    }

    override fun copyThis(): AbstractKotlinElement<KSNode> {
        if (nativeType is KSPropertyDeclaration) {
            val property : KSPropertyDeclaration = nativeType as KSPropertyDeclaration
            return KotlinPropertyElement(
                classElement,
                type,
                property,
                annotationMetadataFactory,
                visitorContext
            )
        } else {
            val getter : KSFunctionDeclaration = nativeType as KSFunctionDeclaration
            return KotlinPropertyElement(
                classElement,
                type,
                name,
                getter,
                setter.map { it.nativeType as KSFunctionDeclaration }.orElse(null),
                annotationMetadataFactory,
                visitorContext
            )
        }
    }

    override fun isAbstract() = abstract
    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): MemberElement {
        return super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as MemberElement
    }
}
