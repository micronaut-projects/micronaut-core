package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement

class KotlinFieldElement(declaration: KSPropertyDeclaration,
                         private val declaringType: ClassElement,
                         annotationMetadata: AnnotationMetadata,
                         visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KSPropertyDeclaration>(declaration, annotationMetadata, visitorContext), FieldElement {

    override fun getName(): String {
        return declaration.simpleName.asString()
    }

    override fun getType(): ClassElement {
       return visitorContext.elementFactory.newClassElement((declaration as KSPropertyDeclaration).type.resolve())
    }

    override fun isPrivate(): Boolean = true

    override fun getDeclaringType() = declaringType
}
