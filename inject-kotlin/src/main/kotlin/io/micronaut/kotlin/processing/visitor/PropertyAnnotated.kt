package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*

class PropertyAnnotated(private val propertyDeclaration: KSPropertyDeclaration) : KSAnnotated {
    override val annotations: Sequence<KSAnnotation>
        get() = propertyDeclaration.annotations.filter { it.useSiteTarget == AnnotationUseSiteTarget.FIELD }
    override val location: Location
        get() = propertyDeclaration.location
    override val origin: Origin
        get() = propertyDeclaration.origin
    override val parent: KSNode?
        get() = propertyDeclaration.parent

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitAnnotated(this, data)
    }
}
