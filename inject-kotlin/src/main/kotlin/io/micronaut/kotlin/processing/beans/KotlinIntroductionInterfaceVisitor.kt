package io.micronaut.kotlin.processing.beans

import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil
import io.micronaut.aop.writer.AopProxyWriter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.AnnotationMetadataReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter

class KotlinIntroductionInterfaceVisitor(private val classElement: ClassElement,
                                         private val annotationMetadata: AnnotationMetadata,
                                         private val aopProxyWriter: AopProxyWriter,
                                         private val visitorContext: VisitorContext) {

    fun visit() {
        classElement.getEnclosedElements(
            ElementQuery.of(MethodElement::class.java)
                .onlyAccessible()
                .filter { method ->
                    method.isAbstract || BeanDefinitionProcessorVisitor.hasAroundStereotype(method, true)
                }
        ).forEach(this::visitMethod)
    }

    private fun visitMethod(methodElement: MethodElement) {
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
            val isInterface: Boolean = classElement.isInterface
            val isDefault: Boolean = methodElement.isDefault
            val owningType = if (isInterface && isDefault) {
                // Default methods cannot be "super" accessed on the defined type
                classElement
            } else {
                methodElement.declaringType
            }

            if (methodElement.isFinal) {
                if (BeanDefinitionProcessorVisitor.hasAroundStereotype(methodElement, true)) {
                    visitorContext.fail(
                        "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                        methodElement
                    )
                } else {
                    if (methodElement.declaringType != classElement) {
                        return
                    }
                    visitorContext.fail(
                        "Public method inherits AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.",
                        methodElement
                    )
                }
                return
            }

            // only apply around advise to non-abstract methods of introduction advise
            aopProxyWriter.visitAroundMethod(
                owningType,
                methodElement.withNewMetadata(annotationMetadata)
            )
        }
    }
}
