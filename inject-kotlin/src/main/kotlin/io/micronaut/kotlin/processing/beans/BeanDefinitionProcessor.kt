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
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.processing.definition.DefaultElementBeanDefinitionBuilderFactory
import io.micronaut.inject.processing.definition.OutputObjectDef
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.ByteCodeWriterUtils
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinNativeElement
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext

internal class BeanDefinitionProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val processed = HashSet<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val visitorContext = KotlinVisitorContext(environment, resolver)

        val elements = resolver.getAllFiles()
            .flatMap { file: KSFile ->
                file.declarations
            }
            .filterIsInstance<KSClassDeclaration>()
            .filterNot { declaration: KSClassDeclaration ->
                declaration.hasAnnotation(AnnotationNames.GENERATED)
            }
            .toList()

        try {
            processClassDeclarations(elements, visitorContext)
        } catch (e: ProcessingException) {
            handleProcessingException(environment, e)
        }
        visitorContext.finish()
        return emptyList()
    }

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
                        .filterNot { declaration: KSClassDeclaration ->
                            declaration.hasAnnotation(AnnotationNames.VETOED)
                        }
                        .filter { declaration ->
                            declaration.isInterface() || declaration.isOuterType() || declaration.isJUnitNestedType()
                        }
                        .toList()
                if (innerClasses.isNotEmpty()) {
                    processClassDeclarations(innerClasses, visitorContext)
                }
                try {
                    val beanDefinitionFactor = DefaultElementBeanDefinitionBuilderFactory(visitorContext)
                    if (processed.add(classElement.name)) {
                        val files = BeanDefinitionCreatorFactory.produce(classElement, beanDefinitionFactor, visitorContext)
                        for (file in files) {
                            write(file, visitorContext, environment)
                        }
                    }
                } catch (e: ProcessingException) {
                    handleProcessingException(environment, e)
                }
            }
        }
    }

    /**
     * Checks if the class declaration has an annotation with the specified name.
     *
     * @param typeName The name of the annotation to check for inclusion.
     * @return `true` if the class declaration includes the specified annotation, `false` otherwise.
     */
    private fun KSClassDeclaration.hasAnnotation(typeName: String): Boolean =
        annotations.any { annotation ->
            annotation.isNamed(typeName)
        }

    /**
     * Checks whether the annotation has the specified type name.
     *
     * @param typeName The fully qualified name of the annotation to match.
     * @return `true` if the annotation's type name matches the specified name, `false` otherwise.
     */
    private fun KSAnnotation.isNamed(typeName: String): Boolean =
        resolveTypeName() == typeName

    /**
     * Checks if the class declaration is an outer type.
     *
     * @return `true` if the class declaration is an outer type, `false` otherwise.
     */
    private fun KSClassDeclaration.isOuterType(): Boolean =
        !isInnerType()

    /**
     * Checks if the class declaration is an inner type.
     *
     * @return `true` if the class declaration is an inner type, `false` otherwise.
     */
    private fun KSClassDeclaration.isInnerType(): Boolean =
        modifiers.contains(Modifier.INNER)

    /**
     * Checks if the class declaration is an interface.
     *
     * @return `true` if the class declaration is an interface, `false` otherwise.
     */
    private fun KSClassDeclaration.isInterface(): Boolean =
        classKind == ClassKind.INTERFACE

    /**
     * Checks if the class declaration is annotated as a JUnit 5 `@Nested` test class.
     *
     * @return `true` if the class declaration has the `@Nested` annotation, `false` otherwise.
     */
    private fun KSClassDeclaration.isJUnitNestedType(): Boolean =
        hasAnnotation(AnnotationNames.NESTED)

    /**
     * Resolves the fully qualified name of the annotation type.
     *
     * @return The fully qualified name of the annotation type as a string, or `null` if the type name cannot be resolved.
     */
    private fun KSAnnotation.resolveTypeName(): String? {
        val annotationType = annotationType.resolve()
        val annotationDeclaration = annotationType.declaration
        val qualifiedName = annotationDeclaration.qualifiedName
        return qualifiedName?.asString()
    }

    private fun write(outputObjectDef: OutputObjectDef, visitorContext: KotlinVisitorContext, environment: SymbolProcessorEnvironment) {
        try {
            val objectDef = outputObjectDef.objectDef
            val serviceClass = outputObjectDef.serviceClass
            val originatingElements = outputObjectDef.originatingElements
            if (serviceClass != null) {
                visitorContext.visitServiceDescriptor(
                    serviceClass,
                    objectDef.getName(),
                    originatingElements.getOriginatingElements()[0]
                )
            }
            visitorContext.visitClass(objectDef.getName(), *originatingElements.getOriginatingElements())
                .use { outputStream ->
                    outputStream.write(ByteCodeWriterUtils.writeByteCode(objectDef, visitorContext))
                }
        } catch (e: Exception) {
            // raise a compile error
            val message = e.message
            var kotlinElement: KSNode? = null
            val astElement = outputObjectDef.originatingElements.getOriginatingElements()[0]
            if (astElement.nativeType is KotlinNativeElement) {
                val nativeElement: KotlinNativeElement = astElement.nativeType as KotlinNativeElement
                kotlinElement = nativeElement.element
            }
            environment.logger.error("Unexpected error: " + (message ?: e.javaClass.getSimpleName()), kotlinElement)
        }
    }

    override fun finish() {
        if (processed.isNotEmpty()) {
            environment.logger.info("Created ${processed.size} bean definitions")
        }
        BeanDefinitionWriter.finish()
    }

    private object AnnotationNames {
        const val NESTED = "org.junit.jupiter.api.Nested"
        const val GENERATED = "io.micronaut.core.annotation.Generated"
        const val VETOED = "io.micronaut.core.annotation.Vetoed"
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
}
