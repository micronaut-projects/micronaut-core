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

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSTopDownVisitor
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Requires.Sdk
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.order.OrderUtil
import io.micronaut.core.util.StringUtils
import io.micronaut.core.version.VersionUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.kotlin.processing.beans.BeanDefinitionProcessor

internal open class TypeElementSymbolProcessor(private val environment: SymbolProcessorEnvironment) :
    SymbolProcessor {

    private lateinit var loadedVisitors: MutableList<LoadedVisitor>
    private var typeElementVisitors: Collection<TypeElementVisitor<*, *>>? = null
    private lateinit var visitorContext: KotlinVisitorContext
    private val processed: MutableSet<String> = mutableSetOf()

    companion object {
        private val SERVICE_LOADER =
            io.micronaut.core.io.service.SoftServiceLoader.load(TypeElementVisitor::class.java)
    }

    open fun newClassElement(
        visitorContext: KotlinVisitorContext,
        classDeclaration: KSClassDeclaration
    ) = visitorContext.elementFactory.newClassElement(
        classDeclaration,
        visitorContext.elementAnnotationMetadataFactory
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // set supported options as system properties to keep compatibility
        // in particular for micronaut-openapi
        environment.options.entries.stream()
            .filter { (key) ->
                key.startsWith(
                    VisitorContext.MICRONAUT_BASE_OPTION_NAME
                )
            }
            .forEach { (key, value) ->
                System.setProperty(
                    key,
                    value
                )
            }

        if (typeElementVisitors == null) {
            typeElementVisitors = findTypeElementVisitors()
            loadedVisitors = ArrayList(typeElementVisitors!!.size)
            visitorContext = KotlinVisitorContext(environment, resolver)
            start()
        }

        if (loadedVisitors.isNotEmpty()) {

            val elements = resolver.getAllFiles()
                .flatMap { file: KSFile -> file.declarations }
                .filterIsInstance<KSClassDeclaration>()
                .filter { declaration: KSClassDeclaration ->
                    declaration.annotations.none { ksAnnotation ->
                        ksAnnotation.shortName.getQualifier() == Generated::class.simpleName
                    }
                }
                .toList()

            if (elements.isNotEmpty()) {
                val classElementsCache: MutableMap<KSClassDeclaration, ClassElement> = HashMap()

                // The visitor X with a higher priority should process elements of A before
                // the visitor Y which is processing elements of B but also using elements A

                // Micronaut Data use-case: EntityMapper with a higher priority needs to process entities first
                // before RepositoryMapper is going to process repositories and read entities

                for (typeElement in elements) {
                    if (typeElement.classKind != ClassKind.ANNOTATION_CLASS) {
                        val className = typeElement.qualifiedName?.asString()
                        if (className != null && !processed.contains(className)) {
                            processed.add(className)

                            for (loadedVisitor in loadedVisitors) {
                                if (!loadedVisitor.matches(typeElement)) {
                                    continue
                                }
                                try {
                                    typeElement.accept(
                                        ElementVisitor(
                                            loadedVisitor,
                                            typeElement,
                                            classElementsCache
                                        ), className
                                    )
                                } catch (e: ProcessingException) {
                                    BeanDefinitionProcessor.handleProcessingException(environment, e)
                                }
                            }
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    override fun finish() {
        for (loadedVisitor in loadedVisitors) {
            try {
                loadedVisitor.visitor.finish(visitorContext)
            } catch (e: ProcessingException) {
                BeanDefinitionProcessor.handleProcessingException(environment, e)
            } catch (e: Throwable) {
                environment.logger.error("Error finalizing type visitor  [${loadedVisitor.visitor}]: ${e.message}")
            }
        }
        processed.clear()
        visitorContext.finish()
    }

    override fun onError() {
        // do nothing
    }

    private fun start() {
        for (visitor in typeElementVisitors!!) {
            try {
                loadedVisitors.add(
                    LoadedVisitor(
                        visitor,
                        visitorContext
                    )
                )
            } catch (e: TypeNotPresentException) {
                // ignored, means annotations referenced are not on the classpath
            } catch (e: NoClassDefFoundError) {
            }

        }

        OrderUtil.reverseSort(loadedVisitors)

        for (loadedVisitor in loadedVisitors) {
            try {
                loadedVisitor.visitor.start(visitorContext)
            } catch (e: Throwable) {
                environment.logger.error("Error initializing type visitor [${loadedVisitor.visitor}]: ${e.message}")
            }
        }
    }

    @NonNull
    private fun findTypeElementVisitors(): Collection<TypeElementVisitor<*, *>> {
        val typeElementVisitors: MutableMap<String, TypeElementVisitor<*, *>> = HashMap(10)
        for (definition in SERVICE_LOADER) {
            if (definition.isPresent) {
                val visitor: TypeElementVisitor<*, *>? = try {
                    definition.load()
                } catch (e: Throwable) {
                    environment.logger.warn("TypeElementVisitor [" + definition.name + "] will be ignored due to loading error: " + e.message)
                    continue
                }
                if (visitor == null || !visitor.isEnabled) {
                    continue
                }
                val requires = visitor.javaClass.getAnnotation(Requires::class.java)
                if (requires != null) {
                    val sdk: Sdk = requires.sdk
                    if (sdk == Sdk.MICRONAUT) {
                        val version: String = requires.version
                        if (StringUtils.isNotEmpty(version) && !VersionUtils.isAtLeastMicronautVersion(
                                version
                            )
                        ) {
                            try {
                                environment.logger.warn("TypeElementVisitor [" + definition.name + "] will be ignored because Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version)
                                continue
                            } catch (e: IllegalArgumentException) {
                                // shouldn't happen, thrown when invalid version encountered
                            }
                        }
                    }
                }
                typeElementVisitors[definition.name] = visitor
            }
        }
        return typeElementVisitors.values
    }

    private inner class ElementVisitor(
        private val loadedVisitor: LoadedVisitor,
        private val classDeclaration: KSClassDeclaration,
        private val classElementsCache: MutableMap<KSClassDeclaration, ClassElement>
    ) : KSTopDownVisitor<Any, Any>() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any): Any {
            if (classDeclaration.qualifiedName!!.asString() == "kotlin.Any") {
                return data
            }
            if (classDeclaration.classKind == ClassKind.ENUM_ENTRY) {
                return data
            }
            if (classDeclaration == this.classDeclaration) {
                val visitorContext = loadedVisitor.visitorContext
                if (loadedVisitor.matches(classDeclaration)) {
                    val classElement = classElementsCache.computeIfAbsent(classDeclaration) { cd ->
                        newClassElement(
                            visitorContext,
                            cd
                        )
                    }

                    try {
                        loadedVisitor.visitor.visitClass(classElement, visitorContext)
                    } catch (e: Exception) {
                        throw ProcessingException(classElement, e.message, e)
                    }

                    classDeclaration.getAllFunctions()
                        .filter { it.isConstructor() && !it.isInternal() }
                        .forEach {
                            visitConstructor(classElement, it)
                        }

                    visitMembers(classElement)
                    val innerClassQuery =
                        ElementQuery.ALL_INNER_CLASSES.onlyStatic()
                            .modifiers { it.contains(ElementModifier.PUBLIC) }
                    val innerClasses = classElement.getEnclosedElements(innerClassQuery)
                    innerClasses.forEach {
                        val visitor = loadedVisitor.visitor
                        val kspClassElement: KotlinClassElement = it as KotlinClassElement
                        if (loadedVisitor.matches(kspClassElement.declaration)) {
                            visitor.visitClass(it, loadedVisitor.visitorContext)
                            visitMembers(it)
                        }
                    }
                }
            }
            return data
        }

        private fun visitMembers(classElement: ClassElement) {
            val properties = classElement.syntheticBeanProperties
            for (property in properties) {
                try {
                    visitNativeProperty(property)
                } catch (e: Exception) {
                    throw ProcessingException(property, e.message, e)
                }
            }
            val memberElements =
                classElement.getEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS)
            for (memberElement in memberElements) {
                when (memberElement) {
                    is FieldElement -> {
                        visitField(memberElement)
                    }

                    is MethodElement -> {
                        visitMethod(memberElement)
                    }
                }
            }
        }

        private fun visitMethod(memberElement: MethodElement) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            if (loadedVisitor.matches(memberElement)) {
                try {
                    visitor.visitMethod(memberElement, visitorContext)
                } catch (e: Exception) {
                    throw ProcessingException(memberElement, e.message)
                }
            }
        }

        private fun visitField(memberElement: FieldElement) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            if (loadedVisitor.matches(memberElement)) {
                try {
                    visitor.visitField(memberElement, visitorContext)
                } catch (e: Exception) {
                    throw ProcessingException(memberElement, e.message)
                }
            }
        }

        private fun visitConstructor(classElement: ClassElement, ctor: KSFunctionDeclaration) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            val ctorElement = visitorContext.elementFactory.newConstructorElement(
                classElement,
                ctor,
                visitorContext.elementAnnotationMetadataFactory
            )
            if (loadedVisitor.matches(ctorElement)) {
                try {
                    visitor.visitConstructor(ctorElement, visitorContext)
                } catch (e: Exception) {
                    throw ProcessingException(ctorElement, e.message)
                }
            }
        }

        fun visitNativeProperty(propertyNode: PropertyElement) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            if (loadedVisitor.matches(propertyNode)) {
                propertyNode.field.ifPresent { visitor.visitField(it, visitorContext) }
                // visit synthetic getter/setter methods
                propertyNode.writeMethod.ifPresent { visitor.visitMethod(it, visitorContext) }
                propertyNode.readMethod.ifPresent { visitor.visitMethod(it, visitorContext) }
            }
        }

        override fun defaultHandler(node: KSNode, data: Any): Any {
            return data
        }

    }
}

