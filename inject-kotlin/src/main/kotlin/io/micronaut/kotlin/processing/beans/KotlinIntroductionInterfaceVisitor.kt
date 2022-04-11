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
package io.micronaut.kotlin.processing.beans

import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil
import io.micronaut.aop.writer.AopProxyWriter
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.AnnotationMetadataReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessorVisitor.Companion.ANN_CONFIGURATION_ADVICE
import java.util.function.Consumer

class KotlinIntroductionInterfaceVisitor(private val classElement: ClassElement,
                                         private val annotationMetadata: AnnotationMetadata,
                                         private val aopProxyWriter: AopProxyWriter,
                                         private val visitorContext: VisitorContext,
                                         private val metadataBuilder: KotlinConfigurationMetadataBuilder) {

    private val isConfigurationProperties = classElement.hasDeclaredStereotype(ConfigurationReader::class.java)
    private val readPrefixes = classElement.getValue(AccessorsStyle::class.java, "readPrefixes", Array<String>::class.java).orElse(arrayOf(AccessorsStyle.DEFAULT_READ_PREFIX))

    fun visit() {
        classElement.getEnclosedElements(
            ElementQuery.of(MethodElement::class.java)
                .onlyAccessible()
        ).forEach(this::visitMethod)
    }

    private fun visitMethod(methodElement: MethodElement) {

        if (isConfigurationProperties) {
            visitConfigPropsMethod(methodElement)
        }

        val annotationMetadata = if (methodElement.annotationMetadata.isEmpty) {
            AnnotationMetadataReference(
                aopProxyWriter.beanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX,
                annotationMetadata
            )
        } else {
            AnnotationMetadataHierarchy(annotationMetadata, methodElement.annotationMetadata)
        }

        if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_AROUND) || annotationMetadata.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            val interceptorTypes =
                InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND)
            aopProxyWriter.visitInterceptorBinding(*interceptorTypes)
        }

        if (methodElement.isAbstract) {
            aopProxyWriter.visitIntroductionMethod(
                methodElement.declaringType,
                methodElement.withNewMetadata(annotationMetadata)
            )
        } else {
            val hasAround = BeanDefinitionProcessorVisitor.hasAroundStereotype(methodElement, true)
            val isExecutable = BeanDefinitionProcessorVisitor.isExecutable(methodElement, classElement)
            if (hasAround) {
                val isInterface: Boolean = classElement.isInterface
                val isDefault: Boolean = methodElement.isDefault
                val owningType = if (isInterface && isDefault) {
                    // Default methods cannot be "super" accessed on the defined type
                    classElement
                } else {
                    methodElement.declaringType
                }

                if (methodElement.isFinal) {
                    visitorContext.fail(
                        "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                        methodElement
                    )
                    return
                }

                // only apply around advise to non-abstract methods of introduction advise
                aopProxyWriter.visitAroundMethod(
                    owningType,
                    methodElement.withNewMetadata(annotationMetadata)
                )
            } else if (isExecutable) {
                aopProxyWriter.visitExecutableMethod(
                    methodElement.declaringType,
                    methodElement,
                    visitorContext
                )
            }
        }
    }

    private fun visitConfigPropsMethod(methodElement: MethodElement) {

        if (methodElement.isAbstract) {
            if (!aopProxyWriter.isValidated) {
                aopProxyWriter.isValidated = BeanDefinitionProcessorVisitor.IS_CONSTRAINT.invoke(methodElement)
            }

            if (!NameUtils.isReaderName(methodElement.name, readPrefixes)) {
                visitorContext.fail("Only getter methods are allowed on @ConfigurationProperties interfaces: $methodElement. You can change the accessors using @AccessorsStyle annotation", methodElement)
                return
            }

            if (methodElement.hasParameters()) {
                visitorContext.fail("Only zero argument getter methods are allowed on @ConfigurationProperties interfaces: $methodElement", methodElement)
                return
            }
            val docComment = methodElement.documentation.orElse(null)
            val propertyName = NameUtils.getPropertyNameForGetter(methodElement.name, readPrefixes);
            val propertyType = methodElement.returnType.name

            if (methodElement.returnType == PrimitiveElement.VOID) {
                visitorContext.fail("Getter methods must return a value @ConfigurationProperties interfaces: $methodElement", methodElement)
                return
            }

            val propertyMetadata = metadataBuilder.visitProperty(
                classElement,
                classElement,
                propertyType,
                propertyName,
                docComment,
                methodElement.stringValue(Bindable::class.java, "defaultValue").orElse(null)
            )

            methodElement.annotate(Property::class.java) { builder: AnnotationValueBuilder<Property> ->
                builder.member(
                    "name",
                    propertyMetadata.path
                )
            }

            methodElement.annotate(ANN_CONFIGURATION_ADVICE) { annBuilder: AnnotationValueBuilder<Annotation> ->
                if (!methodElement.returnType.isPrimitive && methodElement.returnType.hasStereotype(AnnotationUtil.SCOPE)) {
                    annBuilder.member("bean", true)
                }
                if (classElement.hasStereotype(EachProperty::class.java)) {
                    annBuilder.member("iterable", true)
                }
            }
        }
    }
}
