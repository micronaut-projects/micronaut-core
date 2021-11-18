package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Requires.Sdk
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.order.OrderUtil
import io.micronaut.core.util.StringUtils
import io.micronaut.core.version.VersionUtils
import io.micronaut.inject.processing.JavaModelUtils
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors

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

        //resolver.createKSTypeReferenceFromKSType(KSType)

        start()

        if (loadedVisitors.isNotEmpty()) {


            val elements = resolver.getAllFiles()
                .flatMap { file: KSFile -> file.declarations }
                .filter { declaration: KSDeclaration ->
                    declaration is KSClassDeclaration
                }
                .map { declaration: KSDeclaration ->
                    declaration as KSClassDeclaration
                }
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
                        typeElement.accept(ElementVisitor(loadedVisitor), className)
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

    private class ElementVisitor(private val loadedVisitor: LoadedVisitor) : KSDefaultVisitor<Any, Any>() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any): Any {
            val visitorContext = loadedVisitor.visitorContext
            val annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(classDeclaration)
            val classElement = visitorContext.elementFactory.newClassElement(classDeclaration, annotationMetadata)
            loadedVisitor.visitor.visitClass(classElement, visitorContext)
            return data
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Any): Any {
            val visitorContext = loadedVisitor.visitorContext
            var parentDeclaration = function.parentDeclaration
            while (parentDeclaration !is KSClassDeclaration) {
                parentDeclaration = parentDeclaration?.parentDeclaration
            }
            val classAnnotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(parentDeclaration)
            val classElement = visitorContext.elementFactory.newClassElement(parentDeclaration, classAnnotationMetadata)
            val annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(function)

            val methodElement = visitorContext.elementFactory.newMethodElement(classElement, function, annotationMetadata)
            loadedVisitor.visitor.visitMethod(methodElement, visitorContext)
            return data
        }

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Any): Any {
            val visitorContext = loadedVisitor.visitorContext
            var parentDeclaration = property.parentDeclaration
            while (parentDeclaration !is KSClassDeclaration) {
                parentDeclaration = parentDeclaration?.parentDeclaration
            }
            val classAnnotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(parentDeclaration)
            val classElement = visitorContext.elementFactory.newClassElement(parentDeclaration, classAnnotationMetadata)
            val annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(property)

            if (property.getter != null) {
                val methodElement =
                    visitorContext.elementFactory.newMethodElement(classElement, property.getter!!, annotationMetadata)
                loadedVisitor.visitor.visitMethod(methodElement, visitorContext)
            }
            if (property.setter != null) {
                val methodElement =
                    visitorContext.elementFactory.newMethodElement(classElement, property.setter!!, annotationMetadata)
                loadedVisitor.visitor.visitMethod(methodElement, visitorContext)
            }
            return data
        }

        override fun defaultHandler(node: KSNode, data: Any): Any {
            return data
        }

    }
}

