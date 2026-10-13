/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.beans.BeanParameterElement
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilder
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilderFactory
import io.micronaut.inject.utils.BeanInjectionUtils
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * Kotlin implementation of [AbstractBeanDefinitionBuilder].
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
internal open class KotlinBeanDefinitionBuilder(
    originatingElement: Element,
    beanType: ClassElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    private val kotlinVisitorContext: KotlinVisitorContext
) : AbstractBeanDefinitionBuilder(originatingElement, beanType, kotlinVisitorContext, elementAnnotationMetadataFactory) {

    private val annotationMetadataBuilder = kotlinVisitorContext.annotationMetadataBuilder

    init {
        if (kotlinVisitorContext.finishing) {
            throw IllegalStateException(
                "Bean [${beanType.name}] cannot be added once the visitors finish: KSP has invalidated the elements of the compilation " +
                    "after its last round. Add the bean from TypeElementVisitor.finishRound(..) instead"
            )
        }
        if (kotlinVisitorContext.aggregating) {
            kotlinVisitorContext.fail(
                "Cannot add bean definition using addAssociatedBean(..) from a AGGREGATING TypeElementVisitor, consider overriding getVisitorKind()",
                originatingElement
            )
        } else if (javaClass == KotlinBeanDefinitionBuilder::class.java) {
            kotlinVisitorContext.addBeanDefinitionBuilder(this)
        }
    }

    override fun createChildBean(producerField: FieldElement): AbstractBeanDefinitionBuilder {
        val parentType = beanType
        return object : KotlinBeanDefinitionBuilder(
            this@KotlinBeanDefinitionBuilder.originatingElement,
            producerField.genericField.type,
            elementAnnotationMetadataFactory,
            kotlinVisitorContext
        ) {
            override fun getProducingElement(): Element = producerField

            override fun getDeclaringElement(): ClassElement = producerField.declaringType

            @Suppress("UNCHECKED_CAST")
            override fun <R : Any> createBeanDefinitionBuilder(elementBeanDefinitionBuilderFactory: ElementBeanDefinitionBuilderFactory<R>): ElementBeanDefinitionBuilder<R> {
                val newParent = parentType.withAnnotationMetadata(parentType.copyAnnotationMetadata()) // Just a copy
                return elementBeanDefinitionBuilderFactory.factoryField(
                    BeanInjectionUtils.createFieldDefinition(newParent, producerField, !producerField.isPublic, visitorContext)
                ) as ElementBeanDefinitionBuilder<R>
            }
        }
    }

    override fun createChildBean(producerMethod: MethodElement): AbstractBeanDefinitionBuilder {
        val parentType = beanType
        return object : KotlinBeanDefinitionBuilder(
            this@KotlinBeanDefinitionBuilder.originatingElement,
            producerMethod.genericReturnType,
            elementAnnotationMetadataFactory,
            kotlinVisitorContext
        ) {
            private val methodParameters: Array<BeanParameterElement> by lazy {
                initBeanParameters(producerMethod.parameters)
            }

            override fun getProducingElement(): Element = producerMethod

            override fun getDeclaringElement(): ClassElement = producerMethod.declaringType

            override fun getParameters(): Array<BeanParameterElement> = methodParameters

            @Suppress("UNCHECKED_CAST")
            override fun <R : Any> createBeanDefinitionBuilder(elementBeanDefinitionBuilderFactory: ElementBeanDefinitionBuilderFactory<R>): ElementBeanDefinitionBuilder<R> {
                val newParent = parentType.withAnnotationMetadata(parentType.copyAnnotationMetadata()) // Just a copy
                val annotationMetadata = AnnotationMetadataHierarchy(newParent.declaredMetadata, producerMethod.declaredMetadata, getAnnotationMetadata())
                return elementBeanDefinitionBuilderFactory.factoryMethod(
                    BeanInjectionUtils.createMethodDefinition(
                        newParent,
                        producerMethod.withParameters(*parameters).withAnnotationMetadata(annotationMetadata),
                        annotationMetadata,
                        !producerMethod.isPublic,
                        visitorContext
                    )
                ) as ElementBeanDefinitionBuilder<R>
            }
        }
    }

    override fun <T : Annotation> annotate(annotationMetadata: AnnotationMetadata, annotationValue: AnnotationValue<T>) {
        annotationMetadataBuilder.annotate(annotationMetadata, annotationValue)
    }

    override fun <T : Annotation> annotate(annotationMetadata: AnnotationMetadata, annotationType: String, consumer: Consumer<AnnotationValueBuilder<T>>) {
        val builder = AnnotationValue.builder<T>(annotationType)
        consumer.accept(builder)
        annotationMetadataBuilder.annotate(annotationMetadata, builder.build())
    }

    override fun removeStereotype(annotationMetadata: AnnotationMetadata, annotationType: String) {
        annotationMetadataBuilder.removeStereotype(annotationMetadata, annotationType)
    }

    override fun <T : Annotation> removeAnnotationIf(annotationMetadata: AnnotationMetadata, predicate: Predicate<AnnotationValue<T>>) {
        annotationMetadataBuilder.removeAnnotationIf(annotationMetadata, predicate)
    }

    override fun removeAnnotation(annotationMetadata: AnnotationMetadata, annotationType: String) {
        annotationMetadataBuilder.removeAnnotation(annotationMetadata, annotationType)
    }
}
