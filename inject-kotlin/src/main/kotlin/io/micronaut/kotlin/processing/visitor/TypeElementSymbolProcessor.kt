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
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSTopDownVisitor
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Requires.Sdk
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.order.OrderUtil
import io.micronaut.core.util.StringUtils
import io.micronaut.core.version.VersionUtils
import io.micronaut.inject.ast.*
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import java.util.*

class TypeElementSymbolProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    private lateinit var loadedVisitors: MutableList<LoadedVisitor>
    private lateinit var typeElementVisitors: Collection<TypeElementVisitor<*, *>>
    private lateinit var visitorContext: KotlinVisitorContext

    companion object {
        private val SERVICE_LOADER = io.micronaut.core.io.service.SoftServiceLoader.load(TypeElementVisitor::class.java)
    }

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

        typeElementVisitors = findTypeElementVisitors()
        loadedVisitors = ArrayList(typeElementVisitors.size)
        visitorContext = KotlinVisitorContext(environment, resolver)

        start()

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

                // The visitor X with a higher priority should process elements of A before
                // the visitor Y which is processing elements of B but also using elements A

                // Micronaut Data use-case: EntityMapper with a higher priority needs to process entities first
                // before RepositoryMapper is going to process repositories and read entities
                for (loadedVisitor in loadedVisitors) {
                    for (typeElement in elements) {
                        if (!loadedVisitor.matches(typeElement)) {
                            continue
                        }
                        val className = typeElement.qualifiedName.toString()
                        typeElement.accept(ElementVisitor(loadedVisitor, typeElement), className)
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
            } catch (e: Throwable) {
                environment.logger.error("Error finalizing type visitor  [${loadedVisitor.visitor}]: ${e.message}")
            }
        }
        visitorContext.finish()
    }

    override fun onError() {

    }

    private fun start() {
        for (visitor in typeElementVisitors) {
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
                        if (StringUtils.isNotEmpty(version) && !VersionUtils.isAtLeastMicronautVersion(version)) {
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

    private class ElementVisitor(private val loadedVisitor: LoadedVisitor,
    private val classDeclaration: KSClassDeclaration) : KSTopDownVisitor<Any, Any>() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any): Any {
            if (classDeclaration.qualifiedName!!.asString() == "kotlin.Any") {
                return data
            }
            if (classDeclaration.classKind == ClassKind.ENUM_ENTRY) {
                return data
            }
            if (classDeclaration == this.classDeclaration) {
                val visitorContext = loadedVisitor.visitorContext
                val classElement = visitorContext.elementFactory.newClassElement(
                    classDeclaration.asStarProjectedType(),
                    visitorContext.elementAnnotationMetadataFactory
                )
                loadedVisitor.visitor.visitClass(classElement, visitorContext)

                var properties = classElement.syntheticBeanProperties
                for (property in properties) {
                    visitNativeProperty(property)
                }

                classDeclaration.getAllFunctions()
                    .filter { it.isConstructor() }
                    .forEach {
                        visitConstructor(classElement, it)
                    }

                val memberElements = classElement.getEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS)
                for (memberElement in memberElements) {
                    when(memberElement) {
                        is FieldElement -> {
                            visitField(memberElement)
                        }
                        is MethodElement -> {
                            visitMethod(memberElement)
                        }
                    }
                }
            }
            return data
        }

        private fun visitMethod(memberElement: MethodElement) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            if (loadedVisitor.matches(memberElement)) {
                visitor.visitMethod(memberElement, visitorContext)
            }
        }

        private fun visitField(memberElement: FieldElement) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            if (loadedVisitor.matches(memberElement)) {
                visitor.visitField(memberElement, visitorContext)
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
                visitor.visitConstructor(ctorElement, visitorContext)
            }
        }

        fun visitNativeProperty(propertyNode : PropertyElement) {
            val visitor = loadedVisitor.visitor
            val visitorContext = loadedVisitor.visitorContext
            if (loadedVisitor.matches(propertyNode)) {
                propertyNode.field.ifPresent { visitor.visitField(it, visitorContext)}
                // visit synthetic getter/setter methods
                propertyNode.writeMethod.ifPresent { visitor.visitMethod(it, visitorContext)}
                propertyNode.readMethod.ifPresent{ visitor.visitMethod(it, visitorContext)}
            }
        }

        override fun defaultHandler(node: KSNode, data: Any): Any {
            return data
        }

    }
}

