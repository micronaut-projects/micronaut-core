package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.MethodElement
import java.util.*

class KotlinEnumElement(classType: KSType, annotationMetadata: AnnotationMetadata, visitorContext: KotlinVisitorContext): KotlinClassElement(classType, annotationMetadata, visitorContext), EnumElement {

    override fun values(): List<String> {
        return declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .map { decl -> decl.simpleName.asString() }
            .toList()
    }

    override fun getDefaultConstructor(): Optional<MethodElement> {
        return Optional.empty()
    }

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        return Optional.of(KotlinEnumConstructorElement(this))
    }
}
