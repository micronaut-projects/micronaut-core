package io.micronaut.kotlin.processing.annotation

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

class KotlinElementAnnotationMetadataFactory(
    isReadOnly: Boolean,
    metadataBuilder: KotlinAnnotationMetadataBuilder
) : AbstractElementAnnotationMetadataFactory<KSAnnotated, KSAnnotation>(isReadOnly, metadataBuilder) {
    override fun readOnly(): ElementAnnotationMetadataFactory {
        return KotlinElementAnnotationMetadataFactory(true, metadataBuilder as KotlinAnnotationMetadataBuilder)
    }
}
