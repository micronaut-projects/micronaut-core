package io.micronaut.kotlin.processing.beans

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder

class KotlinConfigurationMetadataBuilder: ConfigurationMetadataBuilder<ClassElement>() {

    override fun getOriginatingElements(): Array<Element> {
        return Element.EMPTY_ELEMENT_ARRAY
    }

    override fun buildPropertyPath(
        owningType: ClassElement,
        declaringType: ClassElement,
        propertyName: String
    ): String {
        val value = declaringType.stringValue(ConfigurationReader::class.java, "prefix")
            .orElseGet {
                owningType.stringValue(ConfigurationReader::class.java, "prefix").get()
            }
        return "$value.$propertyName"
    }

    override fun buildTypePath(owningType: ClassElement, declaringType: ClassElement): String {
        val annotationMetadata = if (owningType.isInterface) {
            owningType.annotationMetadata
        } else {
            declaringType.annotationMetadata
        }
        return buildTypePath(owningType, declaringType, annotationMetadata)
    }

    override fun buildTypePath(
        owningType: ClassElement,
        declaringType: ClassElement,
        annotationMetadata: AnnotationMetadata
    ): String {
        val path = StringBuilder(calculateInitialPath(owningType, annotationMetadata))
        val element = if (owningType.isInterface) owningType else declaringType
        prependSuperclasses(element, path)
        var type = declaringType
        while (type.isInner) {
            // we have an inner class, so prepend inner class
            type = type.enclosingType.orElse(null)
            if (type != null) {
                val parentMetadata = type.annotationMetadata
                val parentConfig = parentMetadata.stringValue(ConfigurationReader::class.java)
                if (parentConfig.isPresent) {
                    val parentPath = parentConfig.get()
                    if (parentMetadata.hasDeclaredAnnotation(EachProperty::class.java)) {
                        if (parentMetadata.booleanValue(EachProperty::class.java, "list").orElse(false)) {
                            path.insert(0, "$parentPath[*].")
                        } else {
                            path.insert(0, "$parentPath.*.")
                        }
                    } else {
                        path.insert(0, "$parentPath.")
                    }
                    prependSuperclasses(declaringType, path)
                } else {
                    break
                }
            }
        }
        return path.toString()
    }

    override fun getTypeString(type: ClassElement): String {
        return type.name.replace('$', '.')
    }

    override fun getAnnotationMetadata(type: ClassElement): AnnotationMetadata {
        return type.annotationMetadata
    }

    private fun prependSuperclasses(declaringType: ClassElement, path: StringBuilder) {
        var superType = getSuperClass(declaringType)
        while (superType != null) {
            val parentConfig = superType.stringValue(ConfigurationReader::class.java)
            if (parentConfig.isPresent) {
                path.insert(0, parentConfig.get() + '.')
                superType = getSuperClass(superType)
            } else {
                break
            }
        }
    }

    private fun getSuperClass(declaringType: ClassElement): ClassElement? {
        return if (declaringType.isInterface) {
            declaringType.interfaces.find { it.hasStereotype(ConfigurationReader::class.java) }
        } else {
            declaringType.superType.orElse(null)
        }
    }

    private fun calculateInitialPath(owningType: ClassElement, annotationMetadata: AnnotationMetadata): String {
        return annotationMetadata.stringValue(ConfigurationReader::class.java)
            .map(pathEvaluationFunction(annotationMetadata))
            .orElseGet {
                owningType.stringValue(ConfigurationReader::class.java)
                    .map(pathEvaluationFunction(owningType.annotationMetadata))
                    .orElseGet {
                        pathEvaluationFunction(annotationMetadata).invoke("")
                    }
            }
    }

    private fun pathEvaluationFunction(annotationMetadata: AnnotationMetadata): (String) -> String {
        return { path: String ->
            if (annotationMetadata.hasDeclaredAnnotation(EachProperty::class.java)) {
                if (annotationMetadata.booleanValue(EachProperty::class.java, "list").orElse(false)) {
                    "$path[*]"
                } else {
                    "$path.*"
                }
            } else {
                val prefix = annotationMetadata.stringValue(ConfigurationReader::class.java, "prefix").orElse(null)
                if (StringUtils.isNotEmpty(prefix)) {
                    if (StringUtils.isEmpty(path)) {
                        prefix
                    } else {
                        "$prefix.$path"
                    }
                } else {
                    path
                }
            }
        }
    }
}
