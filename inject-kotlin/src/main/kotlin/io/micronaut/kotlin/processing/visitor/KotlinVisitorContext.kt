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
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.expressions.context.DefaultExpressionCompilationContextFactory
import io.micronaut.expressions.context.ExpressionCompilationContextFactory
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.kotlin.processing.KotlinNativeElementsHelper
import io.micronaut.kotlin.processing.KotlinOutputVisitor
import io.micronaut.kotlin.processing.annotation.KotlinAnnotationMetadataBuilder
import io.micronaut.kotlin.processing.annotation.KotlinElementAnnotationMetadataFactory
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.util.*
import java.util.function.BiConsumer
import kotlin.collections.ArrayList

@OptIn(KspExperimental::class)
internal class KotlinVisitorContext(
    private val environment: SymbolProcessorEnvironment,
    var resolver: Resolver
) : VisitorContext {

    private val visitorAttributes: MutableConvertibleValues<Any> = MutableConvertibleValuesMap()
    private val elementFactory: KotlinElementFactory = KotlinElementFactory(this)
    private val outputVisitor = KotlinOutputVisitor(environment, this)
    val annotationMetadataBuilder = KotlinAnnotationMetadataBuilder(environment, resolver, this)
    private val elementAnnotationMetadataFactory = KotlinElementAnnotationMetadataFactory(false, annotationMetadataBuilder)
    private val expressionCompilationContextFactory = DefaultExpressionCompilationContextFactory(this)
    val nativeElementsHelper = KotlinNativeElementsHelper(resolver)
    var aggregating: Boolean = false

    val extraOpenAnnotations: Array<String> by lazy {
        var allOpenAnnotations = environment.options["kotlin.allopen.annotations"]
        if (allOpenAnnotations.isNullOrEmpty()) {
            allOpenAnnotations = System.getProperty("kotlin.allopen.annotations")
        }
        if (allOpenAnnotations == null) {
            allOpenAnnotations = ""
        }
        allOpenAnnotations.split(",", "|").toTypedArray()
    }

    init {
        try {
            // Workaround for bug in KSP https://github.com/google/ksp/issues/1493
            val resolverImplClass = ClassUtils.forName("com.google.devtools.ksp.processing.impl.ResolverImpl", javaClass.classLoader).orElseThrow()
            val kotlinTypeMapperClass = ClassUtils.forName("org.jetbrains.kotlin.codegen.state.KotlinTypeMapper", javaClass.classLoader).orElseThrow()
            val kotlinTypeMapperInstance = ReflectionUtils.getFieldValue(resolverImplClass, "typeMapper", resolver).orElseThrow()
            try {
                // Pre-2.1.20 field name
                ReflectionUtils.setField(kotlinTypeMapperClass, "useOldManglingRulesForFunctionAcceptingInlineClass", kotlinTypeMapperInstance, false)
            } catch (e: Exception) {
                // Ignore
            }
            // 2.1.20+ field name
            ReflectionUtils.setField(kotlinTypeMapperClass, "useOldInlineClassesManglingScheme", kotlinTypeMapperInstance, false)
        } catch (e: Exception) {
            // Ignore
        }

    }

    fun updateResolver(resolver: Resolver) {
        this.resolver = resolver
        annotationMetadataBuilder.resolver = resolver
        nativeElementsHelper.resolver = resolver
    }

    override fun getLanguage() = VisitorContext.Language.KOTLIN

    override fun <T : Any?> get(
        name: CharSequence?,
        conversionContext: ArgumentConversionContext<T>?
    ): Optional<T> {
        return visitorAttributes.get(name, conversionContext)
    }

    override fun names(): MutableSet<String> {
        return visitorAttributes.names()
    }

    override fun values(): MutableCollection<Any> {
        return visitorAttributes.values()
    }

    override fun put(key: CharSequence?, value: Any?): MutableConvertibleValues<Any> {
        visitorAttributes.put(key, value)
        return this
    }

    override fun remove(key: CharSequence?): MutableConvertibleValues<Any> {
        visitorAttributes.remove(key)
        return this
    }

    override fun clear(): MutableConvertibleValues<Any> {
        visitorAttributes.clear()
        return this
    }

    override fun getClassElement(name: String): Optional<ClassElement> {
        var declaration = resolver.getClassDeclarationByName(name)
        if (declaration == null) {
            declaration = resolver.getClassDeclarationByName(name.replace('$', '.'))
        }
        return Optional.ofNullable(declaration)
            .map(elementFactory::newClassElement)
    }

    @OptIn(KspExperimental::class)
    override fun getClassElements(
        aPackage: String,
        vararg stereotypes: String
    ): Array<ClassElement> {
        return resolver.getDeclarationsFromPackage(aPackage)
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration ->
                declaration.annotations.any { ann ->
                    stereotypes.contains(
                        KotlinAnnotationMetadataBuilder.getAnnotationTypeName(
                            resolver,
                            ann,
                            this
                        )
                    )
                }
            }
            .map { declaration ->
                elementFactory.newClassElement(declaration)
            }
            .toList()
            .toTypedArray()
    }

    override fun getServiceEntries(): MutableMap<String, MutableSet<String>> {
        return outputVisitor.serviceEntries
    }

    override fun visitClass(classname: String, vararg originatingElements: Element): OutputStream {
        return outputVisitor.visitClass(classname, *originatingElements)
    }

    override fun visitServiceDescriptor(type: String, classname: String) {
        outputVisitor.visitServiceDescriptor(type, classname)
    }

    override fun visitServiceDescriptor(
        type: String,
        classname: String,
        originatingElement: Element
    ) {
        outputVisitor.visitServiceDescriptor(type, classname, originatingElement)
    }

    override fun visitMetaInfFile(
        path: String,
        vararg originatingElements: Element
    ): Optional<GeneratedFile> {
        return outputVisitor.visitMetaInfFile(path, *originatingElements)
    }

    override fun visitGeneratedFile(path: String): Optional<GeneratedFile> {
        return outputVisitor.visitGeneratedFile(path)
    }

    override fun visitGeneratedFile(path: String, vararg originatingElements: Element): Optional<GeneratedFile> {
        return outputVisitor.visitGeneratedFile(path, *originatingElements)
    }

    override fun visitGeneratedSourceFile(packageName: String, fileNameWithoutExtension: String, vararg originatingElements: Element): Optional<GeneratedFile> {
        return outputVisitor.visitGeneratedSourceFile(packageName, fileNameWithoutExtension, *originatingElements)
    }

    override fun finish() {
        outputVisitor.finish()
    }

    override fun getClassElement(
        name: String,
        annotationMetadataFactory: ElementAnnotationMetadataFactory
    ): Optional<ClassElement> {
        var declaration = resolver.getClassDeclarationByName(name)
        if (declaration == null) {
            declaration = resolver.getClassDeclarationByName(name.replace('$', '.'))
        }
        return Optional.ofNullable(declaration)
            .map { elementFactory.newClassElement(it, annotationMetadataFactory) }
    }

    override fun getElementFactory(): KotlinElementFactory = elementFactory

    override fun getElementAnnotationMetadataFactory(): ElementAnnotationMetadataFactory {
        return elementAnnotationMetadataFactory
    }

    override fun getExpressionCompilationContextFactory(): ExpressionCompilationContextFactory {
        return expressionCompilationContextFactory
    }

    override fun getAnnotationMetadataBuilder(): AbstractAnnotationMetadataBuilder<*, *> {
        return annotationMetadataBuilder
    }

    override fun getOptions(): Map<String, String> {
        return environment.options
    }

    override fun info(message: String, element: Element?) {
        printMessage(message, environment.logger::info, element)
    }

    fun info(message: String, element: KSNode?) {
        printMessage(message, environment.logger::info, element)
    }

    override fun info(message: String) {
        printMessage(message, environment.logger::info, null as KSNode?)
    }

    override fun fail(message: String, element: Element?) {
        printMessage(message, environment.logger::error, element)
    }

    fun fail(message: String, element: KSNode?) {
        printMessage(message, environment.logger::error, element)
    }

    override fun warn(message: String, element: Element?) {
        printMessage(message, environment.logger::warn, element)
    }

    fun warn(message: String, element: KSNode?) {
        printMessage(message, environment.logger::warn, element)
    }

    private fun printMessage(
        message: String,
        logger: BiConsumer<String, KSNode?>,
        element: Element?
    ) {
        if (element is AbstractKotlinElement<*>) {
            val el = element.nativeType.element
            printMessage(message, logger, el)
        } else {
            printMessage(message, logger, null as KSNode?)
        }
    }

    private fun printMessage(
        message: String,
        logger: BiConsumer<String, KSNode?>,
        element: KSNode?
    ) {
        if (StringUtils.isNotEmpty(message)) {
            logger.accept(message, element)
        }
    }

    open class KspGeneratedFile(
        environment: SymbolProcessorEnvironment,
        elements: MutableList<String>,
        dependencies: Dependencies
    ) : GeneratedFile {

        private val file: File

        init {
            val fileName = elements.removeAt(elements.size - 1)
            val prevFiles = ArrayList(environment.codeGenerator.generatedFile)
            environment.codeGenerator.createNewFile(
                dependencies,
                elements.joinToString("."),
                fileName.substringBeforeLast('.'),
                fileName.substringAfterLast('.', "")
            ).close() // Create an empty file first to find the actual file location
            val newFiles = ArrayList(environment.codeGenerator.generatedFile)
            newFiles.removeAll(prevFiles)
            if (newFiles.size != 1) {
                throw IllegalStateException("Expected to find one file!")
            }
            file = newFiles[0]
        }

        override fun toURI(): URI {
            return file.toURI()
        }

        override fun getName(): String {
            return file.name
        }

        override fun openInputStream(): InputStream {
            return Files.newInputStream(file.toPath())
        }

        override fun openOutputStream(): OutputStream {
            return Files.newOutputStream(file.toPath())
        }

        override fun openReader(): Reader {
            return file.reader()
        }

        override fun getTextContent(): CharSequence {
            return file.readText()
        }

        override fun openWriter(): Writer {
            return OutputStreamWriter(openOutputStream())
        }

    }

}
