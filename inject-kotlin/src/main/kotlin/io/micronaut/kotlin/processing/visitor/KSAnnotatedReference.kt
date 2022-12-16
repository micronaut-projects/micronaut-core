package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*
import io.micronaut.kotlin.processing.getClassDeclaration

open class KSAnnotatedReference(
    open val node : KSAnnotated,
    protected val visitorContext: KotlinVisitorContext)  {
    protected fun tryResolveClassReference(node : KSAnnotated?) : KSClassReference? {
        val dec = node?.getClassDeclaration(visitorContext)
        return if (dec != null) KSClassReference(dec, visitorContext) else null
    }

    protected fun resolveClassReference(node : KSAnnotated) : KSClassReference {
        return KSClassReference(node.getClassDeclaration(visitorContext), visitorContext)
    }
}

class KSClassReference(
    override val node : KSClassDeclaration,
    visitorContext: KotlinVisitorContext) : KSAnnotatedReference(node, visitorContext), KSClassDeclaration by node {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        val otherRef = other as KSClassReference

        if (node.qualifiedName != otherRef.node.qualifiedName) return false
        return true
    }

    override fun hashCode(): Int {
        return node.qualifiedName.hashCode()
    }
}

class KSValueParameterReference(
    override val node: KSValueParameter,
    visitorContext: KotlinVisitorContext
) : KSAnnotatedReference(node, visitorContext), KSValueParameter by node {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KSValueParameterReference

        if (node != other.node) return false

        return true
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }


}

class KSPropertyReference(
    override val node: KSPropertyDeclaration, visitorContext: KotlinVisitorContext)
    : KSAnnotatedReference(node, visitorContext), KSPropertyDeclaration by node {
    private val propertyType : KSClassReference by lazy {
        resolveClassReference(node.type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KSPropertyDeclaration) return false
        if (node.qualifiedName != other.qualifiedName) return false
        val otherType = if (other is KSPropertyReference) other.propertyType else other.type.getClassDeclaration(visitorContext)
        if (propertyType != otherType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = node.qualifiedName.hashCode()
        result = 31 * result + propertyType.hashCode()
        return result
    }
}

class KSPropertySetterReference(
    override val node : KSPropertySetter,
    visitorContext: KotlinVisitorContext) : KSAnnotatedReference(node, visitorContext), KSPropertySetter by node {
    private val prop : KSPropertyReference = KSPropertyReference(node.receiver, visitorContext)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KSPropertySetter) return false

        val otherProp = if (other is KSPropertySetterReference) other.prop else KSPropertyReference(other.receiver, visitorContext)
        if (prop != otherProp) return false
        return true
    }

    override fun hashCode(): Int {
        return prop.hashCode()
    }
}

class KSPropertyGetterReference(
    override val node : KSPropertyGetter,
    visitorContext: KotlinVisitorContext) : KSAnnotatedReference(node, visitorContext), KSPropertyGetter by node {
    private val prop : KSPropertyReference = KSPropertyReference(node.receiver, visitorContext)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KSPropertyGetter) return false

        val otherProp = if (other is KSPropertyGetterReference) other.prop else KSPropertyReference(other.receiver, visitorContext)
        if (prop != otherProp) return false
        return true
    }

    override fun hashCode(): Int {
        return prop.hashCode()
    }
}

class KSFunctionReference(
    override val node: KSFunctionDeclaration,
    visitorContext: KotlinVisitorContext) : KSAnnotatedReference(node, visitorContext), KSFunctionDeclaration by node {
    private val rt :  KSClassReference? by lazy {
        tryResolveClassReference(node.returnType)
    }
    private val params : List<KSValueParameterReference> by lazy {
        node.parameters.map { KSValueParameterReference(it, visitorContext) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KSFunctionReference

        if (node.qualifiedName != other.node.qualifiedName) return false
        if (rt != other.rt) return false
        if (params != other.params) return false

        return true
    }

    override fun hashCode(): Int {
        var result = node.qualifiedName?.hashCode() ?: 0
        result = 31 * result + (rt?.hashCode() ?: 0)
        result = 31 * result + params.hashCode()
        return result
    }


}

