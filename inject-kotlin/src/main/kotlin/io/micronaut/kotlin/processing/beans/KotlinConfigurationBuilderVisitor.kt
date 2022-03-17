package io.micronaut.kotlin.processing.beans

import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.writer.BeanDefinitionVisitor

class KotlinConfigurationBuilderVisitor(
    private val propertyElement: PropertyElement,
    private val configurationMetadataBuilder: KotlinConfigurationMetadataBuilder,
    private val beanDefinitionWriter: BeanDefinitionVisitor) {

    private val allowZeroArgs: Boolean =
        propertyElement.booleanValue(ConfigurationBuilder::class.java, "allowZeroArgs").orElse(false)
    private val configurationPrefix = propertyElement.stringValue(ConfigurationBuilder::class.java, "configurationPrefix")
        .filter(String::isNotEmpty)
        .map { value -> "$value." }
        .orElse("")
    private val includes = propertyElement.stringValues(ConfigurationBuilder::class.java, "includes").toSet()
    private val excludes = propertyElement.stringValues(ConfigurationBuilder::class.java, "excludes").toSet()
    private val prefixes = propertyElement.getValue(AccessorsStyle::class.java, "writePrefixes", Array<String>::class.java)
        .orElse(arrayOf("set"))

    companion object {
        val notDeprecated: (AnnotationMetadata) -> Boolean = { annotationMetadata ->
            !annotationMetadata.hasStereotype(Deprecated::class.java) &&
                    !annotationMetadata.hasStereotype("java.lang.Deprecated")
        }
    }

    fun visit() {
        val classElement = propertyElement.genericType
        classElement.getEnclosedElements(ElementQuery.of(MethodElement::class.java)
            .onlyAccessible()
            .annotated(notDeprecated))
            .forEach(this::visitMethod)
        classElement.getEnclosedElements(ElementQuery.of(PropertyElement::class.java)
            .onlyAccessible()
            .annotated(notDeprecated))
            .forEach(this::visitProperty)
    }

    private fun visitProperty(propertyElement: PropertyElement) {
        propertyElement.writeMethod.ifPresent { writeMethod ->
            visit(writeMethod, propertyElement.name, propertyElement)
        }
    }

    private fun visitMethod(methodElement: MethodElement) {
        if (NameUtils.isWriterName(methodElement.name, prefixes)) {
            val propertyName = NameUtils.getPropertyNameForSetter(methodElement.name, prefixes)
            visit(methodElement, propertyName, methodElement)
        }
    }

    private fun visit(methodElement: MethodElement, propertyName: String, annotationMetadata: AnnotationMetadata) {
        if (shouldExclude(includes, excludes, propertyName)) {
            return
        }

        val params = methodElement.parameters
        val paramCount = params.size
        if (paramCount < 2) {
            val parameter = if (params.size == 1) {
                params[0]
            } else {
                null
            }

            if (parameter == null && !allowZeroArgs) {
                return
            }

            val metadata = configurationMetadataBuilder.visitProperty(
                propertyElement.declaringType,
                methodElement.declaringType,
                parameter?.type?.name,
                configurationPrefix + propertyName,
                null,
                null
            )

            beanDefinitionWriter.visitConfigBuilderMethod(
                getMethodPrefix(methodElement.name),
                methodElement.returnType,
                methodElement.name,
                parameter?.type,
                parameter?.type?.typeArguments,
                metadata.path
            )
        } else if (paramCount == 2) {
            // check the params are a long and a TimeUnit
            val first = params[0]
            val second = params[1]

            if (second.type.name == "java.util.concurrent.TimeUnit" && first.type.name == "long") {
                val metadata = configurationMetadataBuilder.visitProperty(
                    propertyElement.declaringType,
                    methodElement.declaringType,
                    "java.time.Duration",
                    configurationPrefix + propertyName,
                    null,
                    null
                )

                beanDefinitionWriter.visitConfigBuilderDurationMethod(
                    getMethodPrefix(methodElement.name),
                    methodElement.returnType,
                    methodElement.name,
                    metadata.path
                )
            }
        }
    }

    private fun getMethodPrefix(methodName: String): String {
        return prefixes.firstOrNull {
            methodName.startsWith(it)
        } ?: methodName
    }

    private fun shouldExclude(includes: Set<String>, excludes: Set<String>, propertyName: String): Boolean {
        if (includes.isNotEmpty() && !includes.contains(propertyName)) {
            return true
        }
        if (excludes.isNotEmpty() && excludes.contains(propertyName)) {
            return true
        }
        return false
    }
}
