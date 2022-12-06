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
import com.google.devtools.ksp.symbol.*
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Generated
import io.micronaut.inject.processing.BeanDefinitionCreator
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.kotlin.processing.KotlinOutputVisitor
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.io.IOException

class BeanDefinitionProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    private val beanDefinitionMap = mutableMapOf<String, BeanDefinitionCreator>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitorContext = KotlinVisitorContext(environment, resolver)

        val elements = resolver.getAllFiles()
            .flatMap { file: KSFile ->
                file.declarations
            }
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration: KSClassDeclaration ->
                declaration.annotations.none { ksAnnotation ->
                    ksAnnotation.shortName.getQualifier() == Generated::class.simpleName
                }
            }
            .toList()

        processClassDeclarations(elements, visitorContext)
        return emptyList()
    }

    private fun processClassDeclarations(
        elements: List<KSClassDeclaration>,
        visitorContext: KotlinVisitorContext
    ) {
        for (classDeclaration in elements) {
            if (classDeclaration.classKind != ClassKind.ANNOTATION_CLASS) {
                val classElement =
                    visitorContext.elementFactory.newClassElement(classDeclaration.asStarProjectedType()) as KotlinClassElement
                val innerClasses =
                    classDeclaration.declarations.filter { it is KSClassDeclaration }.map { it as KSClassDeclaration }
                        .toList()
                if (innerClasses.isNotEmpty()) {
                    processClassDeclarations(innerClasses, visitorContext)
                }
                beanDefinitionMap.computeIfAbsent(classElement.name) {
                    BeanDefinitionCreatorFactory.produce(classElement, visitorContext)
                }
            }
        }
    }

    override fun finish() {
        try {
            val outputVisitor = KotlinOutputVisitor(environment)
            val processed = HashSet<String>()
            for (beanDefinitionCreator in beanDefinitionMap.values) {
                for (writer in beanDefinitionCreator.build()) {
                    if (processed.add(writer.beanDefinitionName)) {
                        processBeanDefinitions(writer, outputVisitor, processed)
                    } else {
                        throw IllegalStateException("Already processed: " + writer.beanDefinitionName)
                    }
                }
            }
        } catch (e: ProcessingException) {
            environment.logger.error(e.message!!, e.originatingElement as KSNode)
        } finally {
            beanDefinitionMap.clear()
        }
    }

    private fun processBeanDefinitions(
        beanDefinitionWriter: BeanDefinitionVisitor,
        outputVisitor: KotlinOutputVisitor,
        processed: HashSet<String>
    ) {
        try {
            beanDefinitionWriter.visitBeanDefinitionEnd()
            if (beanDefinitionWriter.isEnabled) {
                beanDefinitionWriter.accept(outputVisitor)
                val beanDefinitionReferenceWriter = BeanDefinitionReferenceWriter(beanDefinitionWriter)
                beanDefinitionReferenceWriter.setRequiresMethodProcessing(beanDefinitionWriter.requiresMethodProcessing())
                val className = beanDefinitionReferenceWriter.beanDefinitionQualifiedClassName
                processed.add(className)
                beanDefinitionReferenceWriter.setContextScope(
                    beanDefinitionWriter.annotationMetadata.hasDeclaredAnnotation(Context::class.java)
                )
                beanDefinitionReferenceWriter.accept(outputVisitor)
            }
        } catch (e: IOException) {
            // raise a compile error
            val message = e.message
            error("Unexpected error ${e.javaClass.simpleName}:" + (message ?: e.javaClass.simpleName))
        }
    }

}
