package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder

class KotlinConfigurationMetadataBuilder: ConfigurationMetadataBuilder<ClassElement>() {

    override fun getOriginatingElements(): Array<Element> {
        TODO("Not yet implemented")
    }

    override fun buildPropertyPath(
        owningType: ClassElement?,
        declaringType: ClassElement?,
        propertyName: String?
    ): String {
        TODO("Not yet implemented")
    }

    override fun buildTypePath(owningType: ClassElement?, declaringType: ClassElement?): String {
        TODO("Not yet implemented")
    }

    override fun buildTypePath(
        owningType: ClassElement?,
        declaringType: ClassElement?,
        annotationMetadata: AnnotationMetadata?
    ): String {
        TODO("Not yet implemented")
    }

    override fun getTypeString(type: ClassElement?): String {
        TODO("Not yet implemented")
    }

    override fun getAnnotationMetadata(type: ClassElement?): AnnotationMetadata {
        TODO("Not yet implemented")
    }
}
