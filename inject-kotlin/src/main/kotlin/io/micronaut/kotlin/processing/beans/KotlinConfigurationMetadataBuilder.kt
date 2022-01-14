package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.Element
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder

class KotlinConfigurationMetadataBuilder: ConfigurationMetadataBuilder<KSClassDeclaration>() {

    override fun getOriginatingElements(): Array<Element> {
        TODO("Not yet implemented")
    }

    override fun buildPropertyPath(
        owningType: KSClassDeclaration?,
        declaringType: KSClassDeclaration?,
        propertyName: String?
    ): String {
        TODO("Not yet implemented")
    }

    override fun buildTypePath(owningType: KSClassDeclaration?, declaringType: KSClassDeclaration?): String {
        TODO("Not yet implemented")
    }

    override fun buildTypePath(
        owningType: KSClassDeclaration?,
        declaringType: KSClassDeclaration?,
        annotationMetadata: AnnotationMetadata?
    ): String {
        TODO("Not yet implemented")
    }

    override fun getTypeString(type: KSClassDeclaration?): String {
        TODO("Not yet implemented")
    }

    override fun getAnnotationMetadata(type: KSClassDeclaration?): AnnotationMetadata {
        TODO("Not yet implemented")
    }
}
