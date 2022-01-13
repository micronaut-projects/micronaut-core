package io.micronaut.kotlin.processing.beans

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext

class BeanDefinitionVisitor(private val classDeclaration: KSClassDeclaration,
                            private val visitorContext: KotlinVisitorContext): KSDefaultVisitor<Any, Any>() {

    val beanDefinitionWriters: MutableList<BeanDefinitionWriter> = mutableListOf()
    private val isAopProxyType: Boolean
    private val isExecutableType: Boolean
    private val isDeclaredBean: Boolean
    private val isConfigurationProperties: Boolean
    private val isFactoryClass: Boolean

    init {
        val classElement = visitorContext.elementFactory.newClassElement(classDeclaration.asStarProjectedType())
        this.isAopProxyType = hasAroundStereotype(classElement.annotationMetadata) &&
                !classElement.isAbstract &&
                !classElement.isAssignable(Interceptor::class.java)
        this.isExecutableType = isAopProxyType || classElement.hasStereotype(Executable::class.java)
        this.isConfigurationProperties = classElement.hasDeclaredStereotype(ConfigurationReader::class.java)
        this.isFactoryClass = classElement.hasStereotype(Factory::class.java)
        this.isDeclaredBean = isExecutableType ||
                isConfigurationProperties ||
                isFactoryClass ||
                classElement.hasStereotype(AnnotationUtil.SCOPE) ||
                classElement.hasStereotype(DefaultScope::class.java) ||
                classElement.hasDeclaredStereotype(Bean::class.java) ||
                classElement.primaryConstructor.filter { it.hasStereotype(AnnotationUtil.INJECT) }.isPresent

    }

    companion object {
        fun hasAroundStereotype(annotationMetadata: AnnotationMetadata): Boolean {
            if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_AROUND)) {
                return true
            } else {
                if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
                    return annotationMetadata.getAnnotationValuesByType(InterceptorBinding::class.java)
                        .stream().anyMatch{ av ->
                            av.enumValue("kind", InterceptorKind::class.java).orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND
                        }
                }
            }
            return false
        }
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Any): Any {
        return data
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Any): Any {
        return data
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Any): Any {
        return data
    }

    override fun defaultHandler(node: KSNode, data: Any): Any {
        return data
    }
}
