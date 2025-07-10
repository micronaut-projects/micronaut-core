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

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.Vetoed
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.kotlin.processing.KotlinOutputVisitor
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinNativeElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.io.IOException

internal class BeanDefinitionProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    private val processed = HashSet<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitorContext = KotlinVisitorContext(environment, resolver)

        val elements = resolver.getAllFiles()
            .flatMap { file: KSFile ->
                file.declarations
            }
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration: KSClassDeclaration ->
                declaration.annotations.none { ksAnnotation ->
                    ksAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() == Generated::class.java.name
                }
            }
            .toList()

        try {
            processClassDeclarations(elements, visitorContext)
        } catch (e: ProcessingException) {
            handleProcessingException(environment, e)
        }
        return emptyList()
    }

    private fun isVetoed(ksAnnotation: KSAnnotation) =
        ksAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() == Vetoed::class.java.name

    private fun processClassDeclarations(
        elements: List<KSClassDeclaration>,
        visitorContext: KotlinVisitorContext
    ) {
        for (classDeclaration in elements) {
            if (classDeclaration.classKind != ClassKind.ANNOTATION_CLASS) {
                val classElement =
                    visitorContext.elementFactory.newClassElement(classDeclaration) as KotlinClassElement
                val innerClasses =
                    classDeclaration.declarations
                        .filter { it is KSClassDeclaration }
                        .map { it as KSClassDeclaration }
                        .filter { declaration: KSClassDeclaration ->
                            declaration.annotations.none { ksAnnotation ->
                                isVetoed(ksAnnotation)
                            }
                        }
                        .filter { !it.modifiers.contains(Modifier.INNER) }
                        .toList()
                if (innerClasses.isNotEmpty()) {
                    processClassDeclarations(innerClasses, visitorContext)
                }
                try {
                    val outputVisitor = KotlinOutputVisitor(environment, visitorContext)
                    val produce = BeanDefinitionCreatorFactory.produce(classElement, visitorContext)
                    for (writer in produce.build()) {
                        if (processed.add(writer.beanDefinitionName)) {
                            writer.visitBeanDefinitionEnd()
                            processBeanDefinitions(writer, outputVisitor)
                        }
                    }
                } catch (e: ProcessingException) {
                    handleProcessingException(environment, e)
                }
            }
        }
    }

    override fun finish() {
        if (processed.isNotEmpty()) {
            environment.logger.info("Created ${processed.size} bean definitions")
        }
        BeanDefinitionWriter.finish()
    }

    companion object Helper {
        fun handleProcessingException(environment: SymbolProcessorEnvironment, e: ProcessingException) {
            val message = e.message
            val originatingNode = (e.originatingElement as KotlinNativeElement).element
            if (message != null) {
                environment.logger.error("Originating element: $originatingNode")
                environment.logger.error(message, originatingNode)
                val cause = e.cause
                if (cause != null) {
                    environment.logger.exception(cause)
                }
            } else {
                environment.logger.error("Unknown error processing element", originatingNode)
                val cause = e.cause
                if (cause != null) {
                    environment.logger.exception(cause)
                } else {
                    environment.logger.exception(e)
                }
            }
        }
    }

    private fun processBeanDefinitions(
        beanDefinitionWriter: BeanDefinitionVisitor,
        outputVisitor: KotlinOutputVisitor,
    ) {
        try {
            if (beanDefinitionWriter.isEnabled) {
                beanDefinitionWriter.accept(outputVisitor)
            }
        } catch (e: IOException) {
            // raise a compile error
            val message = e.message
            error("Unexpected error ${e.javaClass.simpleName}:" + (message ?: e.javaClass.simpleName))
        }
    }

}
